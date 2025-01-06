package com.example;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.example.mapper")
public class LambdaSpringConfig {
    // 这里可以根据需要添加更多配置相关代码，目前先保持为空
}