package com.hire.application;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.hire.application.mapper")
@EnableFeignClients(basePackages = "com.hire.api.clients")
public class ApplicationApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApplicationApplication.class, args);
    }
}
