package com.hire.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * Redis 通用操作工具(StringRedisTemplate 封装)。
 *
 * 设计约定:
 * 1. value 一律为字符串:对象用 JsonUtils.toJson 序列化后存取,计数用 increment;
 * 2. key 命名规范:{服务}:{业务}:{标识},如 job:info:123、user:info:456、app:apply:lock:1:2;
 * 3. 所有方法内部捕获异常:缓存是旁路增强,Redis 故障时自动降级,绝不影响主流程——
 *    - get/getLong/increment 异常返回 null(调用方回退查库);
 *    - setIfAbsent 异常返回 true(放行业务,不因缓存故障拦截请求)。
 */
@Slf4j
public class RedisUtil {

    private final StringRedisTemplate redisTemplate;

    public RedisUtil(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 读取字符串,键不存在或 Redis 异常返回 null */
    public String get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis get 失败,key={},原因:{}", key, e.getMessage());
            return null;
        }
    }

    /** 写入字符串并设置过期时间,Redis 异常忽略 */
    public void set(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Redis set 失败,key={},原因:{}", key, e.getMessage());
        }
    }

    /** 写入字符串(不过期),Redis 异常忽略 */
    public void set(String key, String value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.warn("Redis set 失败,key={},原因:{}", key, e.getMessage());
        }
    }

    /**
     * 不存在才写入(NX),用于防连点/互斥锁。
     * 返回 true=抢占成功;Redis 异常时返回 true(降级放行,不阻塞业务)。
     */
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, ttl));
        } catch (Exception e) {
            log.warn("Redis setIfAbsent 失败,key={},原因:{}", key, e.getMessage());
            return true;
        }
    }

    /** 自增 1,返回自增后的值;Redis 异常返回 null(调用方回退 DB 计数) */
    public Long increment(String key) {
        try {
            return redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            log.warn("Redis increment 失败,key={},原因:{}", key, e.getMessage());
            return null;
        }
    }

    /** 读取 Long 值,键不存在、值非数字或 Redis 异常返回 null */
    public Long getLong(String key) {
        String value = get(key);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            log.warn("Redis 值不是数字,key={},value={}", key, value);
            return null;
        }
    }

    /** 设置过期时间,Redis 异常忽略 */
    public void expire(String key, Duration ttl) {
        try {
            redisTemplate.expire(key, ttl);
        } catch (Exception e) {
            log.warn("Redis expire 失败,key={},原因:{}", key, e.getMessage());
        }
    }

    /** 删除键,Redis 异常忽略 */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis delete 失败,key={},原因:{}", key, e.getMessage());
        }
    }

    /**
     * 按模式匹配键(如 job:view:*)。仅用于定时回刷等低频场景;
     * 生产环境大数据量下应改用 SCAN 游标遍历。
     */
    public Set<String> keys(String pattern) {
        try {
            Set<String> result = redisTemplate.keys(pattern);
            return result == null ? Collections.emptySet() : result;
        } catch (Exception e) {
            log.warn("Redis keys 失败,pattern={},原因:{}", pattern, e.getMessage());
            return Collections.emptySet();
        }
    }
}
