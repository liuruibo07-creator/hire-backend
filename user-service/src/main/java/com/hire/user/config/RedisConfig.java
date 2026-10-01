package com.hire.user.config;

import com.hire.common.utils.RedisUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 配置：注册公共 RedisUtil 工具 Bean。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisUtil redisUtil(StringRedisTemplate stringRedisTemplate) {
        return new RedisUtil(stringRedisTemplate);
    }
}
