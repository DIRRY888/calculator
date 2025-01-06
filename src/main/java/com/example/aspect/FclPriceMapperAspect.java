package com.example.aspect;

import com.example.mapper.FclPriceMapper;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Aspect
@Component
public class FclPriceMapperAspect {

    private static final Logger logger = LoggerFactory.getLogger(FclPriceMapperAspect.class);

    // 注入SqlSessionFactory，用于获取MyBatis的配置信息
    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Around("execution(* com.example.mapper.FclPriceMapper.*(..))")
    public Object aroundFclPriceMapperMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        if ("insertOrUpdate".equals(methodName)) {
            Map<String, Object> data = (Map<String, Object>) joinPoint.getArgs()[0];
            logger.info("开始执行insertOrUpdate操作，数据: {}", data);
        } else if ("getAll".equals(methodName)) {
            logger.info("开始执行getAll操作");
        }

        // 获取MyBatis的日志对象，用于后续打印SQL语句
        Log mybatisLog = LogFactory.getLog(FclPriceMapper.class);

        // 获取MyBatis的配置对象，用于获取SQL语句相关信息
        Configuration configuration = sqlSessionFactory.getConfiguration();

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long endTime = System.currentTimeMillis();
            if ("insertOrUpdate".equals(methodName)) {
                if (result instanceof Integer && (Integer) result > 0) {
                    logger.info("insertOrUpdate操作成功，影响行数: {}", result);
                } else {
                    logger.warn("insertOrUpdate操作未影响任何行，数据可能已存在或插入失败");
                }
            } else if ("getAll".equals(methodName)) {
                if (result instanceof List) {
                    List<Map<String, Object>> resultList = (List<Map<String, Object>>) result;
                    logger.info("getAll操作成功，返回结果数量: {}", resultList.size());
                } else {
                    logger.warn("getAll操作返回结果类型异常");
                }
            }

            // 判断当前执行的是哪个方法，尝试获取并打印对应的SQL语句
            MapperMethod.ParamMap<?> paramMap = (MapperMethod.ParamMap<?>) joinPoint.getArgs()[0];
            String sql = "";
            if ("insertOrUpdate".equals(methodName)) {
                sql = configuration.getMappedStatement("com.example.mapper.FclPriceMapper.insertOrUpdate")
                        .getBoundSql(paramMap).getSql();
                mybatisLog.debug(sql);
            } else if ("getAll".equals(methodName)) {
                sql = configuration.getMappedStatement("com.example.mapper.FclPriceMapper.getAll")
                        .getBoundSql(paramMap).getSql();
                mybatisLog.debug(sql);
            }
            logger.info("{}方法执行耗时: {}毫秒", methodName, endTime - startTime);
            return result;
        } catch (Exception e) {
            logger.error("{}操作出现异常", methodName, e);
            throw e;
        }
    }
}