package com.hire.job.config;

import com.hire.common.utils.SnowflakeIdWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 雪花算法ID生成器配置
 */
@Configuration
public class SnowflakeConfig {

    @Value("${cloud.snowflake.worker-id:1}")
    private long workerId;

    @Value("${cloud.snowflake.datacenter-id:1}")
    private long datacenterId;

    @Bean
    public SnowflakeIdWorker snowflakeIdWorker() {
        return new SnowflakeIdWorker(workerId, datacenterId);
    }
}