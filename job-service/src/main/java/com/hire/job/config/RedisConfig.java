package com.hire.job.config;

import com.hire.common.utils.RedisUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 配置：注册公共 RedisUtil 工具 Bean。
 * StringRedisTemplate 由 spring-boot-starter-data-redis 自动装配（Lettuce 连接池，懒连接）。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisUtil redisUtil(StringRedisTemplate stringRedisTemplate) {
        return new RedisUtil(stringRedisTemplate);
    }
}
