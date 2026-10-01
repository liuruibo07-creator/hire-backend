package com.hire.job;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 职位服务启动类
 * 端口 8082,数据库 cloud_job,负责职位CRUD与 Elasticsearch 7.12.1 搜索
 */
@SpringBootApplication
@MapperScan("com.hire.job.mapper")
@EnableFeignClients(basePackages = "com.hire.api.clients")
@EnableScheduling  // 定时任务:浏览量 Redis 计数批量回刷 MySQL(ViewCountFlushTask)
public class JobApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobApplication.class, args);
    }
}