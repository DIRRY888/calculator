package com.example.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Configuration
public class DataSourceConfig {

    @Value("${DB_HOST}")
    private String dbUrl;

    @Value("${DB_USER}")
    private String dbUser;

    @Value("${DB_PASSWORD}")
    private String dbPassword;

    @Value("${DB_NAME}")
    private String dbName;

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}
        dataSource.setUrl("jdbc:mysql://"+dbUrl+":3306/"+dbName);
        dataSource.setUsername(dbUser);
        dataSource.setPassword(dbPassword);
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
// 尝试获取连接并进行简单测试（仅开发调试用，后续可移除）
        try (Connection connection = dataSource.getConnection()) {
             System.out.println("成功获取数据库连接，数据源配置可能正常。");
            // 可以在这里添加更多针对连接的验证逻辑，比如执行一个简单查询等
            // 例如：
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1");
             if (resultSet.next()) {
                 System.out.println("能够执行简单查询，数据库连接功能良好。");
             }
             resultSet.close();
             statement.close();
        } catch (SQLException e) {
            System.err.println("获取数据库连接失败，数据源配置可能存在问题：" + e.getMessage());
        }
        return dataSource;
    }
}