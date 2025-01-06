package com.example;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.example.mapper.FclPriceEntity;
import com.example.mapper.LclPriceEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.example.ExcelHandler.fclPriceMapperStatic;
import static com.example.ExcelHandler.lclPriceMapperStatic;

@Component
public class Handler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final double MPFRATIO = 0.003464;
    private static final double HMFRATIO = 0.003464;

    private static final String TABLE_STYLE = "style=\\\"table-layout: fixed; width: 100%; border-collapse: collapse; word-wrap: break-word; overflow: hidden; text-align: center;\\\"";
    // 定义港口对应关系的Map
    private static final Map<String, String> PORT_MAP = new HashMap<>();
    static {
        PORT_MAP.put("CNFOC", "Fuzhou");
        PORT_MAP.put("CNLYG", "Lianyungang");
        PORT_MAP.put("CNNGB", "Ningbo");
        PORT_MAP.put("CNSHA", "Shanghai");
        PORT_MAP.put("CNTAO", "Qingdao");
        PORT_MAP.put("CNTXG", "Tianjin");
        PORT_MAP.put("CNXMN", "Xiamen");
        PORT_MAP.put("CNYTN", "Yantian");
        PORT_MAP.put("CNZUH", "Zhuhai");
        PORT_MAP.put("CNSZX", "Shenzhen");
        PORT_MAP.put("HKHKG", "Hongkong");
    }
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        try {
            String path = requestEvent.getPath();
            System.out.println(path);
            if (path.equals("/") || path.equals("/login")) {
                // 如果请求路径是根路径或/login，返回登录页面内容
                System.out.println("into /login");
                String content = readFile("public/login.html");
                response.setStatusCode(200);
                response.setHeaders(getDefaultHeaders());
                response.setBody(content);
                response.setIsBase64Encoded(false);
            } else if (path.equals("/calculator")) {
                // 如果请求路径是/calculator且经过Cognito验证（API Gateway处已配置授权），返回计算器页面内容
                System.out.println("into /calculator");
                String content = readFile("public/calculator.html");
                response.setStatusCode(200);
                response.setHeaders(getDefaultHeaders());
                response.setBody(content);
                response.setIsBase64Encoded(false);
            } else if (path.endsWith(".js")) {
                // 处理JavaScript模块文件的请求
                System.out.println("into.js file request");
                response.setStatusCode(200);
                response.setHeaders(Map.of("Content-Type", "application/javascript"));
                String scriptContent = readFile("public/" + path.substring(1));
                response.setBody(scriptContent);
                response.setIsBase64Encoded(false);
            } else if (path.equals("/calculate")) {
                // 处理/calculate路径的POST请求
                System.out.println("请求体内容: " + requestEvent.getBody());
                // 解析请求体中的JSON数据
                Gson gson = new Gson();
                Map<String, Object> requestBody = gson.fromJson(requestEvent.getBody(), HashMap.class);

                // 获取请求数据
                String containerLoadingType = (String) requestBody.get("containerLoadingType");
                String POL = (String) requestBody.get("POL");
                System.out.println(POL);

                // 新增代码，进行港口匹配
                String matchedPortCode = null;
                for (Map.Entry<String, String> entry : PORT_MAP.entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(POL)) {
                        matchedPortCode = entry.getKey();
                        break;
                    }
                }
                if (matchedPortCode!= null) {
                    POL = matchedPortCode;
                }
                System.out.println("matchedPortCode: "+POL);
                String cargoType = (String) requestBody.get("cargoType");
                System.out.println("cargoType: "+cargoType);
                double volume = 0.0;
                double weight = 0.0;
                double value = 0.0;
                int storageDays = 0;
                double customDuty=0.0;
                try {
                    volume = Double.parseDouble((String) requestBody.get("volume"));
                    weight = Double.parseDouble((String) requestBody.get("weight"));
                    value = Integer.parseInt((String) requestBody.get("carton"));

                    Object valuesObj = requestBody.get("values");
                    if (valuesObj instanceof ArrayList) {
                        ArrayList<?> valuesList = (ArrayList<?>) valuesObj;
                        double[] values = new double[valuesList.size()];
                        for (int i = 0; i < valuesList.size(); i++) {
                            if (valuesList.get(i) instanceof Number) {
                                values[i] = ((Number) valuesList.get(i)).doubleValue();
                            } else {
                                // 处理元素不是数值类型的情况，比如返回错误响应等
                                response.setStatusCode(400);
                                response.setBody("Invalid data format for values, element is not a number");
                                return response;
                            }
                        }

                        Object dutyRatesObj = requestBody.get("dutyRates");
                        if (dutyRatesObj instanceof ArrayList) {
                            ArrayList<?> dutyRatesList = (ArrayList<?>) dutyRatesObj;
                            double[] dutyRates = new double[dutyRatesList.size()];
                            for (int i = 0; i < dutyRatesList.size(); i++) {
                                if (dutyRatesList.get(i) instanceof Number) {
                                    dutyRates[i] = ((Number) dutyRatesList.get(i)).doubleValue();
                                } else {
                                    // 处理元素不是数值类型的情况，比如返回错误响应等
                                    response.setStatusCode(400);
                                    response.setBody("Invalid data format for dutyRates, element is not a number");
                                    return response;
                                }
                            }
                            // 计算商品税金总额（新增代码，循环计算乘积并求和）
                            double totalTax = 0.0;
                            for (int i = 0; i < values.length; i++) {
                                totalTax += values[i] * (dutyRates[i] / 100.0);
                            }
                            double mpf = 0.0;
                            for (int i = 0; i < values.length; i++) {
                                mpf += values[i] * MPFRATIO;
                            }
                            // 根据规则取值（新增代码，判断并调整 mpf 的值）
                            if (mpf!= 0) {
                                if (mpf < 25) {
                                    mpf = 25;
                                } else if (mpf > 485) {
                                    mpf = 485;
                                }
                            }
                            double hmf = 0.0;
                            for (int i = 0; i < values.length; i++) {
                                hmf += values[i] * HMFRATIO;
                            }
                            // 根据规则取值（新增代码，判断并调整 hmf 的值）
                            if (hmf!= 0) {
                                if (hmf < 25) {
                                    hmf = 25;
                                }
                            }
                            System.out.println("商品税金总额: " + totalTax + "mpf: " + mpf + "hmf: " + hmf);
                            totalTax = totalTax + mpf + hmf;
                            customDuty = totalTax;
                            // 后续可以使用totalTax这个变量参与其他业务逻辑计算等（比如展示在返回的表格中之类的操作，这里暂时省略相关完善代码）
                            System.out.println("商品税金总额: " + totalTax);
                        }
                    }
                    storageDays = Integer.parseInt((String) requestBody.get("storageDays"));
                } catch (NumberFormatException e) {
                    // 处理转换错误的情况
                    e.printStackTrace();
                    response.setStatusCode(400);
                    response.setBody("Invalid input data");
                    return response;
                }
                String startDate = (String) requestBody.get("startDate");
                String endDate = (String) requestBody.get("endDate");
                String inputMethod = (String) requestBody.get("inputMethod");
                Object tableData = requestBody.get("tableData");
                System.out.println("tableData  " + tableData);
                Object ibFee = requestBody.get("ibFee");
                System.out.println("ibFee  " + ibFee);
                // 定义日期格式
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                // 解析日期
                LocalDate start = LocalDate.parse(startDate, formatter);
                LocalDate end = LocalDate.parse(endDate, formatter);
                // 计算TAD
                long tsd = end.toEpochDay() - start.toEpochDay() + 1;
                System.out.println("tsd  " + tsd);
                int tfd = (int) (tsd - storageDays);
                System.out.println("tfd  " + tfd);
                // 计算FBA存储淡旺季天数
                int fbaPeakSeasonDays = 0;
                int fbaOffPeakSeasonDays = 0;
                LocalDate currentDate = start.plusDays(storageDays);
                System.out.println("currentDate  " + currentDate);
                while (!currentDate.isAfter(end)) {
                    if (currentDate.getMonthValue() >= 10 && currentDate.getMonthValue() <= 12) {
                        fbaPeakSeasonDays++;
                    } else {
                        fbaOffPeakSeasonDays++;
                    }
                    currentDate = currentDate.plusDays(1);
                }
                System.out.println("fbaPeakSeasonDays  " + fbaPeakSeasonDays);
                System.out.println("fbaOffPeakSeasonDays  " + fbaOffPeakSeasonDays);

                // 从开始日期开始计算的淡旺季
                int fbaPeakSeasonDaysFromStart = 0;
                int fbaOffPeakSeasonDaysFromStart = 0;
                LocalDate currentDateFromStart = start;
                while (!currentDateFromStart.isAfter(end)) {
                    if (currentDateFromStart.getMonthValue() >= 10 && currentDateFromStart.getMonthValue() <= 12) {
                        fbaPeakSeasonDaysFromStart++;
                    } else {
                        fbaOffPeakSeasonDaysFromStart++;
                    }
                    currentDateFromStart = currentDateFromStart.plusDays(1);
                }
                System.out.println("fbaPeakSeasonDaysFromStart  " + fbaPeakSeasonDaysFromStart);
                System.out.println("fbaOffPeakSeasonDaysFromStart  " + fbaOffPeakSeasonDaysFromStart);

                BigDecimal ibFeeValue = null; // 用于存储手动输入的ibFee值（如果有的话）
                BigDecimal ibFeeEast = BigDecimal.ZERO; // 用于计算IBFEE EAST
                BigDecimal ibFeeWest = BigDecimal.ZERO; // 用于计算IBFEE WEST
                BigDecimal totalunit = BigDecimal.ZERO; // 用于存储tableData数组元素值总和

                // 判断ibFee是否手动输入
                if (inputMethod.equals("direct")) {
                    // 如果ibFee有值，说明用户直接填写IB FEE，尝试转换为BigDecimal类型
                    if (ibFee instanceof BigDecimal) {
                        ibFeeValue = (BigDecimal) ibFee;
                    } else if (ibFee instanceof Double) {
                        ibFeeValue = BigDecimal.valueOf((Double) ibFee);
                    } else if (ibFee instanceof Integer) {
                        ibFeeValue = BigDecimal.valueOf((Integer) ibFee);
                    } else if (ibFee instanceof String) {
                        try {
                            ibFeeValue = new BigDecimal((String) ibFee);
                        } catch (NumberFormatException ex) {
                            // 处理无法转换为BigDecimal的情况，返回错误响应
                            response.setStatusCode(400);
                            response.setBody("Invalid data format for ibFee");
                            return response;
                        }
                    } else {
                        // 处理其他不支持的类型情况，返回错误响应
                        response.setStatusCode(400);
                        response.setBody("Invalid data type for ibFee");
                        return response;
                    }
                } else {
                    // 如果IB FEE没有值，说明用户输入表格数据，计算IBFEE EAST、IBFEE WEST和totalunit
                    if (tableData!= null) {
                        try {
                            String tableDataJson = tableData.toString();
                            JsonArray tableArray = JsonParser.parseString(tableDataJson).getAsJsonArray();
                            if (tableArray.size() >= 10) {
                                BigDecimal[] eastStandardPrices = {
                                        new BigDecimal("0.21"),
                                        new BigDecimal("0.23"),
                                        new BigDecimal("0.27"),
                                        new BigDecimal("0.32"),
                                        new BigDecimal("0.42"),
                                        new BigDecimal("2.32"),
                                        new BigDecimal("2.74"),
                                        new BigDecimal("3.43"),
                                        new BigDecimal("4.44"),
                                        new BigDecimal("5.21")
                                };
                                BigDecimal[] westStandardPrices = {
                                        new BigDecimal("0.30"),
                                        new BigDecimal("0.34"),
                                        new BigDecimal("0.41"),
                                        new BigDecimal("0.49"),
                                        new BigDecimal("0.68"),
                                        new BigDecimal("2.32"),
                                        new BigDecimal("2.74"),
                                        new BigDecimal("3.43"),
                                        new BigDecimal("4.44"),
                                        new BigDecimal("5.72")
                                };
                                for (int i = 0; i < 10; i++) {
                                    JsonElement element = tableArray.get(i);
                                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                                        BigDecimal tableValue = element.getAsJsonPrimitive().getAsBigDecimal();
                                        ibFeeEast = ibFeeEast.add(tableValue.multiply(eastStandardPrices[i]));
                                        ibFeeWest = ibFeeWest.add(tableValue.multiply(westStandardPrices[i]));
                                        totalunit = totalunit.add(tableValue); // 累加每个元素值到totalunit
                                    } else {
                                        // 处理表格数据中元素不是数值类型的情况，返回错误响应
                                        response.setStatusCode(400);
                                        response.setBody("Invalid data format for tableData element at index " + i);
                                        return response;
                                    }
                                }
                            } else {
                                // 处理表格数据长度不足的情况，返回错误响应
                                response.setStatusCode(400);
                                response.setBody("Invalid tableData length");
                                return response;
                            }
                        } catch (Exception e) {
                            // 处理JSON解析出错等情况，返回错误响应
                            response.setStatusCode(400);
                            response.setBody("Error parsing tableData data");
                            return response;
                        }
                    } else {
                        // 处理tableData为null的情况，返回错误响应
                        response.setStatusCode(400);
                        response.setBody("tableData cannot be null");
                        return response;
                    }
                }
                System.out.println("ibFeeEast : "+ibFeeEast );
                System.out.println("ibFeeWest : "+ibFeeWest );
                System.out.println("totalUnit: "+totalunit );
                String tableHtml = "";
                if (containerLoadingType.equals("FCL")) {

                    double StandardOcean_shipToAWD_美西美东_FCL_AGL_AWD2 = volume * 35.315 * storageDays/30 * 0.36;
                    double StandardOcean_shipToAWD_美西美东_FCL_AGL_AWD3 = value * 2.13;
                    double StandardOcean_shipToAWD_美西美东_FCL_AGL_AWD4 = volume * 35.315 * 0.85;
// 根据数据进行计算，生成表格内容（这里只是示例，实际需根据业务逻辑计算）
                    double FastOcean_AMPAOSS_美西_FCL_AMP快船_AWDStorageFee = 0.0;
                    double FastOcean_MSS_美西_FCL_MSS快船_AWDStorageFee = 0.0;
                    double StandardOcean_AMPAOSS_美西_FCL_AMP普船_AWDStorageFee = 0.0;
                    double StandardOcean_MSS_美西_FCL_MSS普船_AWDStorageFee = 0.0;
                    double StandardOcean_MSS_美东_FCL_MSS普船_AWDStorageFee = 0.0;
                    double StandardOcean_shipToAWD_美西美东_FCL_AGL_AWD = StandardOcean_shipToAWD_美西美东_FCL_AGL_AWD2 + StandardOcean_shipToAWD_美西美东_FCL_AGL_AWD3 + StandardOcean_shipToAWD_美西美东_FCL_AGL_AWD4;
                    double FBA1 = volume * 35.315 * fbaOffPeakSeasonDays/30 * 0.78 + volume * 35.315 * fbaPeakSeasonDays/30 * 2.4;
                    double FBAnoAWD = volume * 35.315 * fbaOffPeakSeasonDaysFromStart/30 * 0.78 + volume * 35.315 * fbaPeakSeasonDaysFromStart/30 * 2.4;

// --获取AMP/AOSS-premium价格1
                    String destination = "USWC";
                    String product = "fcl_amp";
                    String speed_mode = "Premium";

// --使用Mapper查询数据
                    FclPriceEntity priceEntity = fclPriceMapperStatic.getFclPriceByParams(POL, cargoType, destination, product, speed_mode);
                    BigDecimal FastOcean_AMPAOSS_美西_FCL_AMP快船_FreightFeeInboundFee = priceEntity!= null? priceEntity.getFcl_price_per_ctn() : BigDecimal.ZERO;
                    System.out.println(FastOcean_AMPAOSS_美西_FCL_AMP快船_FreightFeeInboundFee);


                    BigDecimal FastOcean_AMPAOSS_美西_FCL_AMP快船_TotalFee = FastOcean_AMPAOSS_美西_FCL_AMP快船_FreightFeeInboundFee.add(new BigDecimal(FastOcean_AMPAOSS_美西_FCL_AMP快船_AWDStorageFee)).add(new BigDecimal(FBAnoAWD)).add(new BigDecimal(customDuty));

// 计算perCBM，处理除数为0的情况
                    String FastOcean_AMPAOSS_美西_FCL_AMP快船_perCBM = volume > 0? FastOcean_AMPAOSS_美西_FCL_AMP快船_TotalFee.divide(new BigDecimal(volume), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算perWeight，处理除数为0的情况
                    String FastOcean_AMPAOSS_美西_FCL_AMP快船_perWeight = weight > 0? FastOcean_AMPAOSS_美西_FCL_AMP快船_TotalFee.divide(new BigDecimal(weight), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算perUnit，处理除数为0的情况
                    String FastOcean_AMPAOSS_美西_FCL_AMP快船_perUnit = totalunit.compareTo(BigDecimal.ZERO) > 0? FastOcean_AMPAOSS_美西_FCL_AMP快船_TotalFee.divide(totalunit, 2, BigDecimal.ROUND_HALF_UP).toString() : "/";

// --获取MSS价格1
                    destination = "USWC";
                    product = "fcl_direct";
                    speed_mode = "Premium";

// --使用Mapper查询数据
                    priceEntity = fclPriceMapperStatic.getFclPriceByParams(POL, cargoType, destination, product, speed_mode);
                    BigDecimal FastOcean_MSS_美西_FCL_MSS快船_FreightFeeInboundFee = priceEntity!= null? priceEntity.getFcl_price_per_ctn() : BigDecimal.ZERO;
                    System.out.println(FastOcean_MSS_美西_FCL_MSS快船_FreightFeeInboundFee);
// 根据是否手动输入ibFee来决定后续使用哪个值作为IB FEE（这里假设后续使用时变量名为 finalIbFee）
                    BigDecimal finalIbFee = ibFeeValue!= null? ibFeeValue : ibFeeWest;
                    System.out.println("finalIbFee: "+finalIbFee);
                    FastOcean_MSS_美西_FCL_MSS快船_FreightFeeInboundFee = FastOcean_MSS_美西_FCL_MSS快船_FreightFeeInboundFee.add(finalIbFee);
                    BigDecimal FastOcean_MSS_美西_FCL_MSS快船_TotalFee = FastOcean_MSS_美西_FCL_MSS快船_FreightFeeInboundFee.add(new BigDecimal(FastOcean_MSS_美西_FCL_MSS快船_AWDStorageFee)).add(new BigDecimal(FBAnoAWD)).add(new BigDecimal(customDuty));

// 计算perCBM，处理除数为0的情况
                    String FastOcean_MSS_美西_FCL_MSS快船_perCBM = volume > 0? FastOcean_MSS_美西_FCL_MSS快船_TotalFee.divide(new BigDecimal(volume), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算perWeight，处理除数为0的情况
                    String FastOcean_MSS_美西_FCL_MSS快船_perWeight = weight > 0? FastOcean_MSS_美西_FCL_MSS快船_TotalFee.divide(new BigDecimal(weight), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算perUnit，处理除数为0的情况
                    String FastOcean_MSS_美西_FCL_MSS快船_perUnit = totalunit.compareTo(BigDecimal.ZERO) > 0? FastOcean_MSS_美西_FCL_MSS快船_TotalFee.divide(totalunit, 2, BigDecimal.ROUND_HALF_UP).toString() : "/";

// --获取AMP/AOSS-standard价格1
                    destination = "USWC";
                    product = "fcl_amp";
                    speed_mode = "Standard";

// --使用Mapper查询数据
                    priceEntity = fclPriceMapperStatic.getFclPriceByParams(POL, cargoType, destination, product, speed_mode);
                    BigDecimal StandardOcean_AMPAOSS_美西_FCL_AMP普船_FreightFeeInboundFee = priceEntity!= null? priceEntity.getFcl_price_per_ctn() : BigDecimal.ZERO;
                    System.out.println(StandardOcean_AMPAOSS_美西_FCL_AMP普船_FreightFeeInboundFee);

                    BigDecimal StandardOcean_AMPAOSS_美西_FCL_AMP普船_TotalFee = StandardOcean_AMPAOSS_美西_FCL_AMP普船_FreightFeeInboundFee.add(new BigDecimal(StandardOcean_AMPAOSS_美西_FCL_AMP普船_AWDStorageFee)).add(new BigDecimal(FBAnoAWD)).add(new BigDecimal(customDuty));

// 计算perCBM，处理除数为0的情况
                    String StandardOcean_AMPAOSS_美西_FCL_AMP普船_perCBM = volume > 0? StandardOcean_AMPAOSS_美西_FCL_AMP普船_TotalFee.divide(new BigDecimal(volume), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算perWeight，处理除数为0的情况
                    String StandardOcean_AMPAOSS_美西_FCL_AMP普船_perWeight = weight > 0? StandardOcean_AMPAOSS_美西_FCL_AMP普船_TotalFee.divide(new BigDecimal(weight), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算perUnit，处理除数为0的情况
                    String StandardOcean_AMPAOSS_美西_FCL_AMP普船_perUnit = totalunit.compareTo(BigDecimal.ZERO) > 0? StandardOcean_AMPAOSS_美西_FCL_AMP普船_TotalFee.divide(totalunit, 2, BigDecimal.ROUND_HALF_UP).toString() : "/";

// --获取MSS-standrad价格1
                    destination = "USWC";
                    product = "fcl_direct";
                    speed_mode = "Standard";

// --使用Mapper查询数据
                    priceEntity = fclPriceMapperStatic.getFclPriceByParams(POL, cargoType, destination, product, speed_mode);
                    BigDecimal StandardOcean_MSS_美西_FCL_MSS普船_FreightFeeInboundFee = priceEntity!= null? priceEntity.getFcl_price_per_ctn() : BigDecimal.ZERO;
                    System.out.println(StandardOcean_MSS_美西_FCL_MSS普船_FreightFeeInboundFee);
// 根据是否手动输入ibFee来决定后续使用哪个值作为IB FEE（这里假设后续使用时变量名为 finalIbFee）
                    finalIbFee = ibFeeValue!= null? ibFeeValue : ibFeeWest;
                    System.out.println("finalIbFee: "+finalIbFee);
                    StandardOcean_MSS_美西_FCL_MSS普船_FreightFeeInboundFee = StandardOcean_MSS_美西_FCL_MSS普船_FreightFeeInboundFee.add(finalIbFee);
                    BigDecimal StandardOcean_MSS_美西_FCL_MSS普船_TotalFee = StandardOcean_MSS_美西_FCL_MSS普船_FreightFeeInboundFee.add(new BigDecimal(StandardOcean_MSS_美西_FCL_MSS普船_AWDStorageFee)).add(new BigDecimal(FBAnoAWD)).add(new BigDecimal(customDuty));

// 计算perCBM，处理除数为0的情况
                    String StandardOcean_MSS_美西_FCL_MSS普船_perCBM = volume > 0? StandardOcean_MSS_美西_FCL_MSS普船_TotalFee.divide(new BigDecimal(volume), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算perWeight，处理除数为0的情况
                    String StandardOcean_MSS_美西_FCL_MSS普船_perWeight = weight > 0? StandardOcean_MSS_美西_FCL_MSS普船_TotalFee.divide(new BigDecimal(weight), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算perUnit，处理除数为0的情况
                    String StandardOcean_MSS_美西_FCL_MSS普船_perUnit = totalunit.compareTo(BigDecimal.ZERO) > 0? StandardOcean_MSS_美西_FCL_MSS普船_TotalFee.divide(totalunit, 2, BigDecimal.ROUND_HALF_UP).toString() : "/";


// --获取MSS-standrad-美东价格1
                    destination = "USEC";
                    product = "fcl_direct";
                    speed_mode = "Standard";

// --使用Mapper查询数据
                    priceEntity = fclPriceMapperStatic.getFclPriceByParams(POL, cargoType, destination, product, speed_mode);
                    BigDecimal StandardOcean_MSS_美东_FCL_MSS普船_FreightFeeInboundFee = priceEntity!= null? priceEntity.getFcl_price_per_ctn() : BigDecimal.ZERO;
                    System.out.println(StandardOcean_MSS_美东_FCL_MSS普船_FreightFeeInboundFee);
                    finalIbFee = ibFeeValue!= null? ibFeeValue : ibFeeEast;
                    System.out.println("finalIbFee: "+finalIbFee);
                    StandardOcean_MSS_美东_FCL_MSS普船_FreightFeeInboundFee = StandardOcean_MSS_美东_FCL_MSS普船_FreightFeeInboundFee.add(finalIbFee);
                    BigDecimal StandardOcean_MSS_美东_FCL_MSS普船_TotalFee = StandardOcean_MSS_美东_FCL_MSS普船_FreightFeeInboundFee.add(new BigDecimal(StandardOcean_MSS_美东_FCL_MSS普船_AWDStorageFee)).add(new BigDecimal(FBAnoAWD)).add(new BigDecimal(customDuty));

// 计算perCBM，处理除数为0的情况
                    String StandardOcean_MSS_美东_FCL_MSS普船_perCBM = volume > 0? StandardOcean_MSS_美东_FCL_MSS普船_TotalFee.divide(new BigDecimal(volume), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算perWeight，处理除数为0的情况
                    String StandardOcean_MSS_美东_FCL_MSS普船_perWeight = weight > 0? StandardOcean_MSS_美东_FCL_MSS普船_TotalFee.divide(new BigDecimal(weight), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算perUnit，处理除数为0的情况
                    String StandardOcean_MSS_美东_FCL_MSS普船_perUnit = totalunit.compareTo(BigDecimal.ZERO) > 0? StandardOcean_MSS_美东_FCL_MSS普船_TotalFee.divide(totalunit, 2, BigDecimal.ROUND_HALF_UP).toString() : "/";

// --获取MSS-standrad-美东价格1
                    destination = "Amazon Distribution Center US";
                    product = "fcl_awd";

// --使用Mapper查询数据
                    priceEntity = fclPriceMapperStatic.getFclPriceByParamsAWD(POL, destination, product);
                    BigDecimal StandardOcean_shipToAWD_美西美东_FCL_AGL_FreightFeeInboundFee = priceEntity!= null? priceEntity.getFcl_price_per_ctn() : BigDecimal.ZERO;
                    System.out.println(StandardOcean_shipToAWD_美西美东_FCL_AGL_FreightFeeInboundFee);

                    BigDecimal StandardOcean_shipToAWD_美西美东_FCL_AGL_TotalFee = StandardOcean_shipToAWD_美西美东_FCL_AGL_FreightFeeInboundFee.add(new BigDecimal(StandardOcean_shipToAWD_美西美东_FCL_AGL_AWD)).add(new BigDecimal(FBA1)).add(new BigDecimal(customDuty));

// 计算perCBM，处理除数为0的情况
                    String StandardOcean_shipToAWD_美西美东_FCL_AGL_perCBM = volume > 0? StandardOcean_shipToAWD_美西美东_FCL_AGL_TotalFee.divide(new BigDecimal(volume), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算perWeight，处理除数为0的情况
                    String StandardOcean_shipToAWD_美西美东_FCL_AGL_perWeight = weight > 0? StandardOcean_shipToAWD_美西美东_FCL_AGL_TotalFee.divide(new BigDecimal(weight), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算perUnit，处理除数为0的情况
                    String StandardOcean_shipToAWD_美西美东_FCL_AGL_perUnit = totalunit.compareTo(BigDecimal.ZERO) > 0? StandardOcean_shipToAWD_美西美东_FCL_AGL_TotalFee.divide(totalunit, 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
                    // 根据获取到的priceEntity进行后续业务逻辑处理，比如使用价格数据参与计算等（这里省略具体计算逻辑完善部分，按实际需求调整）
                    tableHtml +=
                            "<h3>Fast Ocean</h3>"+
                                    "<table " + TABLE_STYLE + ">" +
                                    "<tr>"+
                                    "<th>PO options</th>"+
                                    "<th>Region</th>"+
                                    "<th>Container Type</th>"+
                                    "<th>Shipping Mode</th>"+
                                    "<th>Total Cost (USD)</th>"+
                                    "<th>Per CBM</th>"+
                                    "<th>Per KG</th>"+
                                    "<th>Per Unit</th>"+
                                    "<th>Freight Fee+ Inbound Fee</th>"+
                                    "<th>FBA storage Fee</th>"+
                                    "<th>AWD Fee</th>"+
                                    "<th>Custom Duty</th>" +
                                    "</tr>"+
                                    "<tr>"+
                                    "<td>AMP/AOSS</td>"+
                                    "<td>美西</td>"+
                                    "<td>FCL</td>"+
                                    "<td>AMP 快船</td>"+
                                    "<td>"+FastOcean_AMPAOSS_美西_FCL_AMP快船_TotalFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+FastOcean_AMPAOSS_美西_FCL_AMP快船_perCBM+"</td>"+
                                    "<td>"+FastOcean_AMPAOSS_美西_FCL_AMP快船_perWeight+"</td>"+
                                    "<td>"+FastOcean_AMPAOSS_美西_FCL_AMP快船_perUnit+"</td>"+
                                    "<td>"+FastOcean_AMPAOSS_美西_FCL_AMP快船_FreightFeeInboundFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+String.format("%.2f",FBAnoAWD)+"</td>"+
                                    "<td>"+String.format("%.2f",FastOcean_AMPAOSS_美西_FCL_AMP快船_AWDStorageFee)+"</td>"+
                                    "<td>"+customDuty+"</td>"+
                                    "</tr>"+
                                    "<tr>"+
                                    "<td>MSS</td>"+
                                    "<td>美西</td>"+
                                    "<td>FCL</td>"+
                                    "<td>MSS 快船</td>"+
                                    "<td>"+FastOcean_MSS_美西_FCL_MSS快船_TotalFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+FastOcean_MSS_美西_FCL_MSS快船_perCBM+"</td>"+
                                    "<td>"+FastOcean_MSS_美西_FCL_MSS快船_perWeight+"</td>"+
                                    "<td>"+FastOcean_MSS_美西_FCL_MSS快船_perUnit+"</td>"+
                                    "<td>"+FastOcean_MSS_美西_FCL_MSS快船_FreightFeeInboundFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+String.format("%.2f",FBAnoAWD)+"</td>"+
                                    "<td>"+String.format("%.2f",FastOcean_MSS_美西_FCL_MSS快船_AWDStorageFee)+"</td>"+
                                    "<td>"+customDuty+"</td>"+
                                    "</tr>"+
                                    "</table>"+
                                    "<h3>Standard Ocean</h3>"+
                                    "<table " + TABLE_STYLE + "><tr>"+
                                    "<th>PO options</th>"+
                                    "<th>Region</th>"+
                                    "<th>Container Type</th>"+
                                    "<th>Shipping Mode</th>"+
                                    "<th>Total Cost (USD)</th>"+
                                    "<th>Per CBM</th>"+
                                    "<th>Per KG</th>"+
                                    "<th>Per Unit</th>"+
                                    "<th>Freight Fee+ Inbound Fee</th>"+
                                    "<th>FBA storage Fee</th>"+
                                    "<th>AWD Fee</th>"+
                                    "<th>Custom Duty</th>" +
                                    "</tr>"+
                                    "<tr>"+
                                    "<td>AMP/AOSS</td>"+
                                    "<td>美西</td>"+
                                    "<td>FCL</td>"+
                                    "<td>AMP 普船</td>"+
                                    "<td>"+StandardOcean_AMPAOSS_美西_FCL_AMP普船_TotalFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+StandardOcean_AMPAOSS_美西_FCL_AMP普船_perCBM+"</td>"+
                                    "<td>"+StandardOcean_AMPAOSS_美西_FCL_AMP普船_perWeight+"</td>"+
                                    "<td>"+StandardOcean_AMPAOSS_美西_FCL_AMP普船_perUnit+"</td>"+
                                    "<td>"+StandardOcean_AMPAOSS_美西_FCL_AMP普船_FreightFeeInboundFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+String.format("%.2f",FBAnoAWD)+"</td>"+
                                    "<td>"+String.format("%.2f",StandardOcean_AMPAOSS_美西_FCL_AMP普船_AWDStorageFee)+"</td>"+
                                    "<td>"+customDuty+"</td>"+
                                    "</tr>"+
                                    "<tr>"+
                                    "<td>MSS</td>"+
                                    "<td>美西</td>"+
                                    "<td>FCL</td>"+
                                    "<td>MSS 普船</td>"+
                                    "<td>"+StandardOcean_MSS_美西_FCL_MSS普船_TotalFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+StandardOcean_MSS_美西_FCL_MSS普船_perCBM+"</td>"+
                                    "<td>"+StandardOcean_MSS_美西_FCL_MSS普船_perWeight+"</td>"+
                                    "<td>"+StandardOcean_MSS_美西_FCL_MSS普船_perUnit+"</td>"+
                                    "<td>"+StandardOcean_MSS_美西_FCL_MSS普船_FreightFeeInboundFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+String.format("%.2f", FBAnoAWD)+"</td>"+
                                    "<td>"+String.format("%.2f",StandardOcean_MSS_美西_FCL_MSS普船_AWDStorageFee)+"</td>"+
                                    "<td>"+customDuty+"</td>"+
                                    "</tr>"+
                                    "<tr>"+
                                    "<td>MSS</td>"+
                                    "<td>美东</td>"+
                                    "<td>FCL</td>"+
                                    "<td>MSS 普船</td>"+
                                    "<td>"+StandardOcean_MSS_美东_FCL_MSS普船_TotalFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+StandardOcean_MSS_美东_FCL_MSS普船_perCBM+"</td>"+
                                    "<td>"+StandardOcean_MSS_美东_FCL_MSS普船_perWeight+"</td>"+
                                    "<td>"+StandardOcean_MSS_美东_FCL_MSS普船_perUnit+"</td>"+
                                    "<td>"+StandardOcean_MSS_美东_FCL_MSS普船_FreightFeeInboundFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+String.format("%.2f", FBAnoAWD)+"</td>"+
                                    "<td>"+String.format("%.2f", StandardOcean_MSS_美东_FCL_MSS普船_AWDStorageFee)+"</td>"+
                                    "<td>"+customDuty+"</td>"+
                                    "</tr>"+
                                    "<tr>"+
                                    "<td>ship to AWD</td>"+
                                    "<td>美西/美东</td>"+
                                    "<td>FCL</td>"+
                                    "<td>AGL AWD</td>"+
                                    "<td>"+StandardOcean_shipToAWD_美西美东_FCL_AGL_TotalFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+StandardOcean_shipToAWD_美西美东_FCL_AGL_perCBM+"</td>"+
                                    "<td>"+StandardOcean_shipToAWD_美西美东_FCL_AGL_perWeight+"</td>"+
                                    "<td>"+StandardOcean_shipToAWD_美西美东_FCL_AGL_perUnit+"</td>"+
                                    "<td>"+StandardOcean_shipToAWD_美西美东_FCL_AGL_FreightFeeInboundFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+String.format("%.2f", FBA1)+"</td>"+
                                    "<td>"+String.format("%.2f", StandardOcean_shipToAWD_美西美东_FCL_AGL_AWD)+"</td>"+
                                    "<td>"+customDuty+"</td>"+
                                    "</tr>"+
                                    "</table>"+
                                    "<a href=\\\"https://www.wjx.cn/vm/QTYj6Wo.aspx#\\\">点击参与问卷调查</a>";
                }

                if (containerLoadingType.equals("LCL")) {
                    double StandardOcean_shipToAWD_美西美东_LCL_AGL_AWD2 = volume * 35.315 * storageDays/30 * 0.36;
                    double StandardOcean_shipToAWD_美西美东_LCL_AGL_AWD3 = value * 2.13;
                    double StandardOcean_shipToAWD_美西美东_LCL_AGL_AWD4 = volume * 35.315 * 0.85;
// 根据数据进行计算，生成表格内容（这里只是示例，实际需根据业务逻辑计算）
                    double FastOcean_AMPAOSS_美西_LCL_AMP快船_AWDStorageFee = 0.0;
                    double FastOcean_MSS_美西_LCL_MSS快船_AWDStorageFee = 0.0;
                    double StandardOcean_AMPAOSS_美西_LCL_AMP普船_AWDStorageFee = 0.0;
                    double StandardOcean_MSS_美西_LCL_MSS普船_AWDStorageFee = 0.0;
                    double StandardOcean_MSS_美东_LCL_MSS普船_AWDStorageFee = 0.0;
                    double StandardOcean_shipToAWD_美西美东_LCL_AGL_AWD = StandardOcean_shipToAWD_美西美东_LCL_AGL_AWD2 + StandardOcean_shipToAWD_美西美东_LCL_AGL_AWD3 + StandardOcean_shipToAWD_美西美东_LCL_AGL_AWD4;
                    double FBA1 = volume * 35.315 * fbaOffPeakSeasonDays/30 * 0.78 + volume * 35.315 * fbaPeakSeasonDays/30 * 2.4;
                    double FBAnoAWD = volume * 35.315 * fbaOffPeakSeasonDaysFromStart/30 * 0.78 + volume * 35.315 * fbaPeakSeasonDaysFromStart/30 * 2.4;

// --获取AMP/AOSS-premium价格1
                    String destination = "USWC";
                    String product = "lcl_amp";
                    String speed_mode = "Premium";

// --使用Mapper查询数据
                    LclPriceEntity priceEntity = lclPriceMapperStatic.getLclPriceByParams(POL, cargoType, destination, product, speed_mode);
                    BigDecimal importClearancePerBl = priceEntity!= null? priceEntity.getImport_clearance_per_bl() : BigDecimal.ZERO;
                    BigDecimal exportClearancePerBl = priceEntity!= null? priceEntity.getExport_clearance_per_bl() : BigDecimal.ZERO;
                    BigDecimal lclFee = priceEntity!= null? calculateLclFee(volume, priceEntity) : BigDecimal.ZERO;
                    BigDecimal FastOcean_AMPAOSS_美西_LCL_AMP快船_FreightFeeInboundFee = importClearancePerBl.add(exportClearancePerBl).add(lclFee);
                    System.out.println(importClearancePerBl + "---" + exportClearancePerBl + "---" + lclFee);
                    System.out.println(FastOcean_AMPAOSS_美西_LCL_AMP快船_FreightFeeInboundFee);

                    BigDecimal FastOcean_AMPAOSS_美西_LCL_AMP快船_TotalFee = FastOcean_AMPAOSS_美西_LCL_AMP快船_FreightFeeInboundFee.add(new BigDecimal(FastOcean_AMPAOSS_美西_LCL_AMP快船_AWDStorageFee)).add(new BigDecimal(FBAnoAWD)).add(new BigDecimal(customDuty));

// 计算FastOcean_AMPAOSS_美西_LCL_AMP快船_TotalFee对应的perCBM，处理除数为0的情况
                    String FastOcean_AMPAOSS_美西_LCL_AMP快船_perCBM = volume > 0? FastOcean_AMPAOSS_美西_LCL_AMP快船_TotalFee.divide(new BigDecimal(volume), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算FastOcean_AMPAOSS_美西_LCL_AMP快船_TotalFee对应的perWeight，处理除数为0的情况
                    String FastOcean_AMPAOSS_美西_LCL_AMP快船_perWeight = weight > 0? FastOcean_AMPAOSS_美西_LCL_AMP快船_TotalFee.divide(new BigDecimal(weight), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算FastOcean_AMPAOSS_美西_LCL_AMP快船_TotalFee对应的perUnit，处理除数为0的情况
                    String FastOcean_AMPAOSS_美西_LCL_AMP快船_perUnit = totalunit.compareTo(BigDecimal.ZERO) > 0? FastOcean_AMPAOSS_美西_LCL_AMP快船_TotalFee.divide(totalunit, 2, BigDecimal.ROUND_HALF_UP).toString() : "/";

// --获取MSS价格1
                    destination = "USWC";
                    product = "lcl_direct";
                    speed_mode = "Premium";

// --使用Mapper查询数据
                    priceEntity = lclPriceMapperStatic.getLclPriceByParams(POL, cargoType, destination, product, speed_mode);
                    importClearancePerBl = priceEntity!= null? priceEntity.getImport_clearance_per_bl() : BigDecimal.ZERO;
                    exportClearancePerBl = priceEntity!= null? priceEntity.getExport_clearance_per_bl() : BigDecimal.ZERO;
                    lclFee = priceEntity!= null? calculateLclFee(volume, priceEntity) : BigDecimal.ZERO;
                    BigDecimal FastOcean_MSS_美西_LCL_MSS快船_FreightFeeInboundFee = importClearancePerBl.add(exportClearancePerBl).add(lclFee);
                    System.out.println(importClearancePerBl + "---" + exportClearancePerBl + "---" + lclFee);
                    System.out.println(FastOcean_MSS_美西_LCL_MSS快船_FreightFeeInboundFee);
// 根据是否手动输入ibFee来决定后续使用哪个值作为IB FEE（这里假设后续使用时变量名为 finalIbFee）
                    BigDecimal finalIbFee = ibFeeValue!= null? ibFeeValue : ibFeeWest;
                    System.out.println("finalIbFee: "+finalIbFee);
                    FastOcean_MSS_美西_LCL_MSS快船_FreightFeeInboundFee = FastOcean_MSS_美西_LCL_MSS快船_FreightFeeInboundFee.add(finalIbFee);
                    BigDecimal FastOcean_MSS_美西_LCL_MSS快船_TotalFee = FastOcean_MSS_美西_LCL_MSS快船_FreightFeeInboundFee.add(new BigDecimal(FastOcean_MSS_美西_LCL_MSS快船_AWDStorageFee)).add(new BigDecimal(FBAnoAWD)).add(new BigDecimal(customDuty));

// 计算FastOcean_MSS_美西_LCL_MSS快船_TotalFee对应的perCBM，处理除数为0的情况
                    String FastOcean_MSS_美西_LCL_MSS快船_perCBM = volume > 0? FastOcean_MSS_美西_LCL_MSS快船_TotalFee.divide(new BigDecimal(volume), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算FastOcean_MSS_美西_LCL_MSS快船_TotalFee对应的perWeight，处理除数为0的情况
                    String FastOcean_MSS_美西_LCL_MSS快船_perWeight = weight > 0? FastOcean_MSS_美西_LCL_MSS快船_TotalFee.divide(new BigDecimal(weight), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算FastOcean_MSS_美西_LCL_MSS快船_TotalFee对应的perUnit，处理除数为0的情况
                    String FastOcean_MSS_美西_LCL_MSS快船_perUnit = totalunit.compareTo(BigDecimal.ZERO) > 0? FastOcean_MSS_美西_LCL_MSS快船_TotalFee.divide(totalunit, 2, BigDecimal.ROUND_HALF_UP).toString() : "/";

// --获取AMP/AOSS-standard价格1
                    destination = "USWC";
                    product = "lcl_amp";
                    speed_mode = "Standard";

// --使用Mapper查询数据
                    priceEntity = lclPriceMapperStatic.getLclPriceByParams(POL, cargoType, destination, product, speed_mode);
                    importClearancePerBl = priceEntity!= null? priceEntity.getImport_clearance_per_bl() : BigDecimal.ZERO;
                    exportClearancePerBl = priceEntity!= null? priceEntity.getExport_clearance_per_bl() : BigDecimal.ZERO;
                    lclFee = priceEntity!= null? calculateLclFee(volume, priceEntity) : BigDecimal.ZERO;
                    BigDecimal StandardOcean_AMPAOSS_美西_LCL_AMP普船_FreightFeeInboundFee = importClearancePerBl.add(exportClearancePerBl).add(lclFee);
                    System.out.println(importClearancePerBl + "---" + exportClearancePerBl + "---" + lclFee);
                    System.out.println(StandardOcean_AMPAOSS_美西_LCL_AMP普船_FreightFeeInboundFee);

                    BigDecimal StandardOcean_AMPAOSS_美西_LCL_AMP普船_TotalFee = StandardOcean_AMPAOSS_美西_LCL_AMP普船_FreightFeeInboundFee.add(new BigDecimal(StandardOcean_AMPAOSS_美西_LCL_AMP普船_AWDStorageFee)).add(new BigDecimal(FBAnoAWD)).add(new BigDecimal(customDuty));

// 计算StandardOcean_AMPAOSS_美西_LCL_AMP普船_TotalFee对应的perCBM，处理除数为0的情况
                    String StandardOcean_AMPAOSS_美西_LCL_AMP普船_perCBM = volume > 0? StandardOcean_AMPAOSS_美西_LCL_AMP普船_TotalFee.divide(new BigDecimal(volume), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算StandardOcean_AMPAOSS_美西_LCL_AMP普船_TotalFee对应的perWeight，处理除数为0的情况
                    String StandardOcean_AMPAOSS_美西_LCL_AMP普船_perWeight = weight > 0? StandardOcean_AMPAOSS_美西_LCL_AMP普船_TotalFee.divide(new BigDecimal(weight), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算StandardOcean_AMPAOSS_美西_LCL_AMP普船_TotalFee对应的perUnit，处理除数为0的情况
                    String StandardOcean_AMPAOSS_美西_LCL_AMP普船_perUnit = totalunit.compareTo(BigDecimal.ZERO) > 0? StandardOcean_AMPAOSS_美西_LCL_AMP普船_TotalFee.divide(totalunit, 2, BigDecimal.ROUND_HALF_UP).toString() : "/";

// --获取MSS-standrad价格1
                    destination = "USWC";
                    product = "lcl_direct";
                    speed_mode = "Standard";

// --使用Mapper查询数据
                    priceEntity = lclPriceMapperStatic.getLclPriceByParams(POL, cargoType, destination, product, speed_mode);
                    importClearancePerBl = priceEntity!= null? priceEntity.getImport_clearance_per_bl() : BigDecimal.ZERO;
                    exportClearancePerBl = priceEntity!= null? priceEntity.getExport_clearance_per_bl() : BigDecimal.ZERO;
                    lclFee = priceEntity!= null? calculateLclFee(volume, priceEntity) : BigDecimal.ZERO;
                    BigDecimal StandardOcean_MSS_美西_LCL_MSS普船_FreightFeeInboundFee = importClearancePerBl.add(exportClearancePerBl).add(lclFee);
                    System.out.println(importClearancePerBl + "---" + exportClearancePerBl + "---" + lclFee);
                    System.out.println(StandardOcean_MSS_美西_LCL_MSS普船_FreightFeeInboundFee);
                    finalIbFee = ibFeeValue!= null? ibFeeValue : ibFeeWest;
                    System.out.println("finalIbFee: "+finalIbFee);
                    StandardOcean_MSS_美西_LCL_MSS普船_FreightFeeInboundFee = StandardOcean_MSS_美西_LCL_MSS普船_FreightFeeInboundFee.add(finalIbFee);
                    BigDecimal StandardOcean_MSS_美西_LCL_MSS普船_TotalFee = StandardOcean_MSS_美西_LCL_MSS普船_FreightFeeInboundFee.add(new BigDecimal(StandardOcean_MSS_美西_LCL_MSS普船_AWDStorageFee)).add(new BigDecimal(FBAnoAWD)).add(new BigDecimal(customDuty));

// 计算StandardOcean_MSS_美西_LCL_MSS普船_TotalFee对应的perCBM，处理除数为0的情况
                    String StandardOcean_MSS_美西_LCL_MSS普船_perCBM = volume > 0? StandardOcean_MSS_美西_LCL_MSS普船_TotalFee.divide(new BigDecimal(volume), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算StandardOcean_MSS_美西_LCL_MSS普船_TotalFee对应的perWeight，处理除数为0的情况
                    String StandardOcean_MSS_美西_LCL_MSS普船_perWeight = weight > 0? StandardOcean_MSS_美西_LCL_MSS普船_TotalFee.divide(new BigDecimal(weight), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算StandardOcean_MSS_美西_LCL_MSS普船_TotalFee对应的perUnit，处理除数为0的情况
                    String StandardOcean_MSS_美西_LCL_MSS普船_perUnit = totalunit.compareTo(BigDecimal.ZERO) > 0? StandardOcean_MSS_美西_LCL_MSS普船_TotalFee.divide(totalunit, 2, BigDecimal.ROUND_HALF_UP).toString() : "/";


// --获取MSS-standrad-美东价格1
                    destination = "USEC";
                    product = "lcl_direct";
                    speed_mode = "Standard";

// --使用Mapper查询数据
                    priceEntity = lclPriceMapperStatic.getLclPriceByParams(POL, cargoType, destination, product, speed_mode);
                    importClearancePerBl = priceEntity!= null? priceEntity.getImport_clearance_per_bl() : BigDecimal.ZERO;
                    exportClearancePerBl = priceEntity!= null? priceEntity.getExport_clearance_per_bl() : BigDecimal.ZERO;
                    lclFee = priceEntity!= null? calculateLclFee(volume, priceEntity) : BigDecimal.ZERO;
                    BigDecimal StandardOcean_MSS_美东_LCL_MSS普船_FreightFeeInboundFee = importClearancePerBl.add(exportClearancePerBl).add(lclFee);
                    System.out.println(importClearancePerBl + "---" + exportClearancePerBl + "---" + lclFee);
                    System.out.println(StandardOcean_MSS_美东_LCL_MSS普船_FreightFeeInboundFee);
                    finalIbFee = ibFeeValue!= null? ibFeeValue : ibFeeEast;
                    System.out.println("finalIbFee: "+finalIbFee);
                    StandardOcean_MSS_美东_LCL_MSS普船_FreightFeeInboundFee = StandardOcean_MSS_美东_LCL_MSS普船_FreightFeeInboundFee.add(finalIbFee);
                    BigDecimal StandardOcean_MSS_美东_LCL_MSS普船_TotalFee = StandardOcean_MSS_美东_LCL_MSS普船_FreightFeeInboundFee.add(new BigDecimal(StandardOcean_MSS_美东_LCL_MSS普船_AWDStorageFee)).add(new BigDecimal(FBAnoAWD)).add(new BigDecimal(customDuty));

// 计算StandardOcean_MSS_美东_LCL_MSS普船_TotalFee对应的perCBM，处理除数为0的情况
                    String StandardOcean_MSS_美东_LCL_MSS普船_perCBM = volume > 0? StandardOcean_MSS_美东_LCL_MSS普船_TotalFee.divide(new BigDecimal(volume), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算StandardOcean_MSS_美东_LCL_MSS普船_TotalFee对应的perWeight，处理除数为0的情况
                    String StandardOcean_MSS_美东_LCL_MSS普船_perWeight = weight > 0? StandardOcean_MSS_美东_LCL_MSS普船_TotalFee.divide(new BigDecimal(weight), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算StandardOcean_MSS_美东_LCL_MSS普船_TotalFee对应的perUnit，处理除数为0的情况
                    String StandardOcean_MSS_美东_LCL_MSS普船_perUnit = totalunit.compareTo(BigDecimal.ZERO) > 0? StandardOcean_MSS_美东_LCL_MSS普船_TotalFee.divide(totalunit, 2, BigDecimal.ROUND_HALF_UP).toString() : "/";

// --获取MSS-standrad-美东价格1
                    destination = "Amazon Distribution Center US";
                    product = "lcl_awd";

// --使用Mapper查询数据
                    priceEntity = lclPriceMapperStatic.getLclPriceByParamsAWD(POL, destination, product);
                    importClearancePerBl = priceEntity!= null? priceEntity.getImport_clearance_per_bl() : BigDecimal.ZERO;
                    exportClearancePerBl = priceEntity!= null? priceEntity.getExport_clearance_per_bl() : BigDecimal.ZERO;
                    lclFee = priceEntity!= null? calculateLclFee(volume, priceEntity) : BigDecimal.ZERO;
                    BigDecimal StandardOcean_shipToAWD_美西美东_LCL_AGL_FreightFeeInboundFee = importClearancePerBl.add(exportClearancePerBl).add(lclFee);
                    System.out.println(importClearancePerBl + "---" + exportClearancePerBl + "---" + lclFee);
                    System.out.println(StandardOcean_shipToAWD_美西美东_LCL_AGL_FreightFeeInboundFee);

                    BigDecimal StandardOcean_shipToAWD_美西美东_LCL_AGL_TotalFee = StandardOcean_shipToAWD_美西美东_LCL_AGL_FreightFeeInboundFee.add(new BigDecimal(StandardOcean_shipToAWD_美西美东_LCL_AGL_AWD)).add(new BigDecimal(FBA1)).add(new BigDecimal(customDuty));

// 计算StandardOcean_shipToAWD_美西美东_LCL_AGL_TotalFee对应的perCBM，处理除数为0的情况
                    String StandardOcean_shipToAWD_美西美东_LCL_AGL_perCBM = volume > 0? StandardOcean_shipToAWD_美西美东_LCL_AGL_TotalFee.divide(new BigDecimal(volume), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
// 计算StandardOcean_shipToAWD_美西美东_LCL_AGL_Total
                    String StandardOcean_shipToAWD_美西美东_LCL_AGL_perWeight = weight > 0? StandardOcean_shipToAWD_美西美东_LCL_AGL_TotalFee.divide(new BigDecimal(weight), 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
                    String StandardOcean_shipToAWD_美西美东_LCL_AGL_perUnit = totalunit.compareTo(BigDecimal.ZERO) > 0? StandardOcean_shipToAWD_美西美东_LCL_AGL_TotalFee.divide(totalunit, 2, BigDecimal.ROUND_HALF_UP).toString() : "/";
                    tableHtml +=
                            "<h3>Fast Ocean</h3>"+
                                    "<table " + TABLE_STYLE + ">" +
                                    "<tr>"+
                                    "<th>PO options</th>"+
                                    "<th>Region</th>"+
                                    "<th>Container Type</th>"+
                                    "<th>Shipping Mode</th>"+
                                    "<th>Total Cost (USD)</th>"+
                                    "<th>Per CBM</th>"+
                                    "<th>Per KG</th>"+
                                    "<th>Per Unit</th>"+
                                    "<th>Freight Fee+ Inbound Fee</th>"+
                                    "<th>FBA storage Fee</th>"+
                                    "<th>AWD Fee</th>"+
                                    "<th>Custom Duty</th>" +
                                    "</tr>"+
                                    "<tr>"+
                                    "<td>AMP/AOSS</td>"+
                                    "<td>美西</td>"+
                                    "<td>LCL</td>"+
                                    "<td>AMP 快船</td>"+
                                    "<td>"+FastOcean_AMPAOSS_美西_LCL_AMP快船_TotalFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+FastOcean_AMPAOSS_美西_LCL_AMP快船_perCBM+"</td>"+
                                    "<td>"+FastOcean_AMPAOSS_美西_LCL_AMP快船_perWeight+"</td>"+
                                    "<td>"+FastOcean_AMPAOSS_美西_LCL_AMP快船_perUnit+"</td>"+
                                    "<td>"+FastOcean_AMPAOSS_美西_LCL_AMP快船_FreightFeeInboundFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+String.format("%.2f", FBAnoAWD)+"</td>"+
                                    "<td>"+String.format("%.2f", FastOcean_AMPAOSS_美西_LCL_AMP快船_AWDStorageFee)+"</td>"+
                                    "<td>"+customDuty+"</td>"+
                                    "</tr>"+
                                    "<tr>"+
                                    "<td>MSS</td>"+
                                    "<td>美西</td>"+
                                    "<td>LCL</td>"+
                                    "<td>MSS 快船</td>"+
                                    "<td>"+FastOcean_MSS_美西_LCL_MSS快船_TotalFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+FastOcean_MSS_美西_LCL_MSS快船_perCBM+"</td>"+
                                    "<td>"+FastOcean_MSS_美西_LCL_MSS快船_perWeight+"</td>"+
                                    "<td>"+FastOcean_MSS_美西_LCL_MSS快船_perUnit+"</td>"+
                                    "<td>"+FastOcean_MSS_美西_LCL_MSS快船_FreightFeeInboundFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+String.format("%.2f", FBAnoAWD)+"</td>"+
                                    "<td>"+String.format("%.2f", FastOcean_MSS_美西_LCL_MSS快船_AWDStorageFee)+"</td>"+
                                    "<td>"+customDuty+"</td>"+
                                    "</tr>"+
                                    "</table>"+
                                    "<h3>Standard Ocean</h3>"+
                                    "<table " + TABLE_STYLE + "><tr>"+
                                    "<th>PO options</th>"+
                                    "<th>Region</th>"+
                                    "<th>Container Type</th>"+
                                    "<th>Shipping Mode</th>"+
                                    "<th>Total Cost (USD)</th>"+
                                    "<th>Per CBM</th>"+
                                    "<th>Per KG</th>"+
                                    "<th>Per Unit</th>"+
                                    "<th>Freight Fee+ Inbound Fee</th>"+
                                    "<th>FBA storage Fee</th>"+
                                    "<th>AWD Fee</th>"+
                                    "<th>Custom Duty</th>" +
                                    "</tr>"+
                                    "<tr>"+
                                    "<td>AMP/AOSS</td>"+
                                    "<td>美西</td>"+
                                    "<td>LCL</td>"+
                                    "<td>AMP 普船</td>"+
                                    "<td>"+StandardOcean_AMPAOSS_美西_LCL_AMP普船_TotalFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+StandardOcean_AMPAOSS_美西_LCL_AMP普船_perCBM+"</td>"+
                                    "<td>"+StandardOcean_AMPAOSS_美西_LCL_AMP普船_perWeight+"</td>"+
                                    "<td>"+StandardOcean_AMPAOSS_美西_LCL_AMP普船_perUnit+"</td>"+
                                    "<td>"+StandardOcean_AMPAOSS_美西_LCL_AMP普船_FreightFeeInboundFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+String.format("%.2f", FBAnoAWD)+"</td>"+
                                    "<td>"+String.format("%.2f", StandardOcean_AMPAOSS_美西_LCL_AMP普船_AWDStorageFee)+"</td>"+
                                    "<td>"+customDuty+"</td>"+
                                    "</tr>"+
                                    "<tr>"+
                                    "<td>MSS</td>"+
                                    "<td>美西</td>"+
                                    "<td>LCL</td>"+
                                    "<td>MSS 普船</td>"+
                                    "<td>"+StandardOcean_MSS_美西_LCL_MSS普船_TotalFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+StandardOcean_MSS_美西_LCL_MSS普船_perCBM+"</td>"+
                                    "<td>"+StandardOcean_MSS_美西_LCL_MSS普船_perWeight+"</td>"+
                                    "<td>"+StandardOcean_MSS_美西_LCL_MSS普船_perUnit+"</td>"+
                                    "<td>"+StandardOcean_MSS_美西_LCL_MSS普船_FreightFeeInboundFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+String.format("%.2f", FBAnoAWD)+"</td>"+
                                    "<td>"+String.format("%.2f", StandardOcean_MSS_美西_LCL_MSS普船_AWDStorageFee)+"</td>"+
                                    "<td>"+customDuty+"</td>"+
                                    "</tr>"+
                                    "<tr>"+
                                    "<td>MSS</td>"+
                                    "<td>美东</td>"+
                                    "<td>LCL</td>"+
                                    "<td>MSS 普船</td>"+
                                    "<td>"+StandardOcean_MSS_美东_LCL_MSS普船_TotalFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+StandardOcean_MSS_美东_LCL_MSS普船_perCBM+"</td>"+
                                    "<td>"+StandardOcean_MSS_美东_LCL_MSS普船_perWeight+"</td>"+
                                    "<td>"+StandardOcean_MSS_美东_LCL_MSS普船_perUnit+"</td>"+
                                    "<td>"+StandardOcean_MSS_美东_LCL_MSS普船_FreightFeeInboundFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+String.format("%.2f", FBAnoAWD)+"</td>"+
                                    "<td>"+String.format("%.2f", StandardOcean_MSS_美东_LCL_MSS普船_AWDStorageFee)+"</td>"+
                                    "<td>"+customDuty+"</td>"+
                                    "</tr>"+
                                    "<tr>"+
                                    "<td>ship to AWD</td>"+
                                    "<td>美西/美东</td>"+
                                    "<td>LCL</td>"+
                                    "<td>AGL AWD</td>"+
                                    "<td>"+StandardOcean_shipToAWD_美西美东_LCL_AGL_TotalFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+StandardOcean_shipToAWD_美西美东_LCL_AGL_perCBM+"</td>"+
                                    "<td>"+StandardOcean_shipToAWD_美西美东_LCL_AGL_perWeight+"</td>"+
                                    "<td>"+StandardOcean_shipToAWD_美西美东_LCL_AGL_perUnit+"</td>"+
                                    "<td>"+StandardOcean_shipToAWD_美西美东_LCL_AGL_FreightFeeInboundFee.setScale(2, RoundingMode.HALF_UP)+"</td>"+
                                    "<td>"+String.format("%.2f", FBA1)+"</td>"+
                                    "<td>"+String.format("%.2f", StandardOcean_shipToAWD_美西美东_LCL_AGL_AWD)+"</td>"+
                                    "<td>"+customDuty+"</td>"+
                                    "</tr>"+
                                    "</table>"+
                                    "<a href=\\\"https://www.wjx.cn/vm/QTYj6Wo.aspx#\\\">点击参与问卷调查</a>";
                }

                // 设置响应
                response.setStatusCode(200);
                response.setHeaders(getDefaultHeaders());
                response.setBody("{\"tableHtml\": \"" + tableHtml + "\"}");
                response.setIsBase64Encoded(false);
            } else {
                // 处理其他路径情况，可按需完善，比如返回404等
                response.setStatusCode(404);
                response.setBody("Not Found");
            }
        } catch (IOException e) {
            e.printStackTrace();
            response.setStatusCode(500);
            response.setBody("Internal Server Error");
        }
        return response;
    }

    private static double[] parseDoubleArray(Object arrayObj) {
        if (arrayObj instanceof JsonArray) {
            JsonArray jsonArray = (JsonArray) arrayObj;
            double[] resultArray = new double[jsonArray.size()];
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonElement element = jsonArray.get(i);
                System.out.println("正在解析values数组中的第 " + (i + 1) + " 个元素，其类型为: " + element.getClass().getName());
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                    resultArray[i] = element.getAsJsonPrimitive().getAsDouble();
                } else {
                    System.out.println("该元素不符合数字类型要求，解析失败");
                    return null;
                }
            }
            return resultArray;
        }
        System.out.println("传入的对象不是JsonArray类型，解析失败");
        return null;
    }

    private String readFile(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
    }

    private Map<String, String> getDefaultHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/html");
        return headers;
    }

    public static BigDecimal calculateLclFee(double volume, LclPriceEntity entity) {
        BigDecimal fee;
        if (volume < 5) {
            // 这里假设你有对应的小于5cbm的单价（你可以根据实际情况替换具体数值或者从其他地方获取这个单价）
            fee = entity.getLess_than_5cbm_per_cbm().multiply(new BigDecimal(volume));
        } else if (volume >= 5 && volume < 10) {
            // 5 - 10cbm的单价（同样示例单价，按需替换）
            fee = entity.getBetween_5_10cbm_per_cbm().multiply(new BigDecimal(volume));
        } else if (volume >= 10 && volume < 15) {
            // 10 - 15cbm的单价
            fee = entity.getBetween_10_15cbm_per_cbm().multiply(new BigDecimal(volume));
        } else {
            // 大于15cbm的单价
            fee = entity.getGreater_than_15cbm_per_cbm().multiply(new BigDecimal(volume));
        }
        return fee;
    }
}
