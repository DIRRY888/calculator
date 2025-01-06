package com.example;

import com.example.mapper.FclPriceMapper;
import com.example.mapper.LclPriceMapper;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

import javax.annotation.PostConstruct;

@Component
@SpringBootApplication
public class ExcelHandler implements RequestHandler<S3Event, String> {

    private static final Logger logger = LoggerFactory.getLogger(ExcelHandler.class);

    public static FclPriceMapper fclPriceMapperStatic;

    public static LclPriceMapper lclPriceMapperStatic;

    @Autowired
    private FclPriceMapper fclPriceMapper;

    @Autowired
    private LclPriceMapper lclPriceMapper;

    private static ConfigurableApplicationContext applicationContext;

    @PostConstruct
    public void init(){
        logger.info("before fclPriceMapperStatic = {}",fclPriceMapperStatic);
        logger.info("before fclPriceMapper = {}",fclPriceMapper);
        fclPriceMapperStatic = fclPriceMapper;
        lclPriceMapperStatic = lclPriceMapper;
        logger.info("after fclPriceMapperStatic = {}",fclPriceMapperStatic);
        logger.info("after fclPriceMapper = {}",fclPriceMapper);
    }

    static {
        if (applicationContext == null) {
            SpringApplication springApplication = new SpringApplication(ExcelHandler.class);
            applicationContext = springApplication.run();
            logger.info("Spring Boot application context initialized successfully.");
            logger.info("fclPriceMapperStatic2 = {}",fclPriceMapperStatic);
            logger.info("lclPriceMapperStatic2 = {}",lclPriceMapperStatic);
        }
    }


    @Override
    public String handleRequest(S3Event event, Context context) {
        AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();

        for (S3Event.S3EventNotificationRecord record : event.getRecords()) {
            String s3ObjectKey = record.getS3().getObject().getKey();
            logger.info("开始处理S3对象，对象键为: {}", s3ObjectKey);
            S3Object s3Object = s3Client.getObject(new GetObjectRequest("calculator-handle-excel", s3ObjectKey));
            InputStream inputStream = s3Object.getObjectContent();
            logger.info("成功获取S3对象内容的输入流");

            try {
                logger.info("创建输入流，准备创建Workbook对象");
                Workbook workbook = WorkbookFactory.create(inputStream);
                logger.info("成功创建Workbook对象");

                // 处理FCL_PRICE工作表
                Sheet fclSheet = workbook.getSheet("fcl_price");
                if (fclSheet!= null) {
                    logger.info("开始处理FCL_PRICE工作表");
                    processSheet(fclSheet);
                } else {
                    logger.info("FCL_PRICE工作表不存在，跳过处理");
                }

                // 处理LCL_PRICE工作表
                Sheet lclSheet = workbook.getSheet("lcl_price");
                if (lclSheet!= null) {
                    logger.info("开始处理LCL_PRICE工作表");
                    processSheet(lclSheet);
                } else {
                    logger.info("LCL_PRICE工作表不存在，跳过处理");
                }

                workbook.close();
                logger.info("已关闭Workbook对象");
            } catch (Exception e) {
                logger.error("处理Excel文件时发生错误", e);
                return "Error processing Excel file";
            }
        }

        return "Excel data processed successfully";
    }

    public void processSheet(Sheet sheet) {
        logger.info("进入processSheet方法，sheet对象: {}", sheet);
//        logger.info("进入processSheet方法，mapper对象: {}", mapper);
        if (sheet == null) {
            logger.info("传入的工作表为null，直接返回，不进行数据处理");
            return;
        }

        logger.info("开始逐行处理工作表中的数据");

        // 获取第一行作为表头行
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            logger.error("工作表中不存在表头行，无法处理数据");
            return;
        }

        int rowIndex = 1; // 从行号为1的行开始处理数据，因为行号为0是表头行
        while (rowIndex <= sheet.getLastRowNum()) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                logger.info("当前行（行号: {}）为空，跳过该行处理", rowIndex);
                rowIndex++;
                continue;
            }

            Map<String, Object> data = new HashMap<>();
            logger.info("开始处理当前行的数据，行号: {}", rowIndex);
            for (Cell cell : row) {
                int columnIndex = cell.getColumnIndex();
                String columnName = headerRow.getCell(columnIndex).getStringCellValue();
                if ("VLOOKUP（勿动）".equals(columnName)) {
                    columnName = "VLOOKUP";
                }
                logger.info("columnName: {}", columnName);
                logger.info("cell: {}", cell);
                logger.info("getCellValue(cell): {}", getCellValue(cell));
                data.put(columnName, getCellValue(cell));
            }
            logger.info("data: {}", data);
            try {
                // 根据VLOOKUP对应的值判断使用哪个mapper
                String vlookupValue = (String) data.get("VLOOKUP");
                if (vlookupValue!= null) {
                    if (vlookupValue.contains("fcl")) {
                        fclPriceMapperStatic.insertOrUpdate(data);
                        logger.info("成功使用fclPriceMapperStatic插入或更新数据: {}", data);
                    } else if (vlookupValue.contains("lcl")) {
                        // 这里假设你有对应的lclMapper，且有insertOrUpdate方法，按需调整
                        lclPriceMapperStatic.insertOrUpdate(data);
                        logger.info("成功使用lclMapper插入或更新数据: {}", data);
                    } else {
                        logger.error("VLOOKUP对应的值不符合预期格式，无法确定使用哪个mapper，数据: {}", data);
                    }
                } else {
                    logger.error("VLOOKUP对应的值为空或格式不正确，数据: {}", data);
                }
            } catch (Exception e) {
                logger.error("在插入或更新数据时出现异常，数据: {}", data, e);
            }
            rowIndex++;
        }
    }

    private Object getCellValue(Cell cell) {
        if (cell == null) {
            logger.info("单元格为null，返回 null");
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                // 处理纯数字类型，返回数字值
                if (!DateUtil.isCellDateFormatted(cell)) {
                    return cell.getNumericCellValue();
                }

                // 处理日期类型，转换并返回日期对象
                try {
                    Date date = cell.getDateCellValue();
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
                    return sdf.format(date);
                } catch (IllegalStateException e) {
                    // 如果上述转换失败，返回数字值
                    return cell.getNumericCellValue();
                }

            case BLANK:
                return null;
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case ERROR:
                return null;
            case FORMULA:
                try {
                    // 获取公式的计算结果类型
                    CellType formulaResultType = cell.getCachedFormulaResultType();

                    // 根据结果类型进行不同处理
                    switch (formulaResultType) {
                        case STRING:
                            return cell.getStringCellValue();
                        case NUMERIC:
                            // 对于公式结果为日期类型的情况，进行特殊处理
                            if (DateUtil.isCellDateFormatted(cell)) {
                                Date date = cell.getDateCellValue();
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
                                return sdf.format(date);
                            }
                            return cell.getNumericCellValue();
                        case BOOLEAN:
                            return cell.getBooleanCellValue();
                        case BLANK:
                            return null;
                        case ERROR:
                            return null;
                        default:
                            return null;
                    }
                } catch (Exception e) {
                    logger.error("处理FORMULA类型单元格时出错", e);
                    return null;
                }
            default:
                return null;
        }
    }


    // 这是一个示例方法，你需要根据实际情况替换为你的数据插入或更新逻辑
    private void insertOrUpdateData(Map<String, Object> data) {
        // 假设这里是数据插入或更新的具体逻辑，例如调用某个服务或数据库操作
        logger.info("插入或更新数据: {}", data);
    }
}