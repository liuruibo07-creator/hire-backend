package com.hire.chat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableFeignClients(basePackages = "com.hire.api.clients")
@MapperScan("com.hire.chat.mapper")
public class ChatApplication {
    public static void main(String[] args) { SpringApplication.run(ChatApplication.class, args); }
}
