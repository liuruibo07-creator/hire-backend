package com.hire.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 登录接口限流(固定窗口计数):
 * 仅对登录路径生效,同一来源 IP 每分钟最多 {@link #MAX_PER_MINUTE} 次,超过返回 429。
 * Redis 异常时降级放行,不影响登录主流程(限流是安全增强,不是可用性依赖)。
 *
 * 顺序说明:getOrder() 返回 -10,早于 UserLoginGlobalFilter(order=0),
 * 保证不带 token 的暴力尝试也会被计数,而不是先被 401 拦掉。
 */
@Slf4j
@Component
public class LoginRateLimitGlobalFilter implements GlobalFilter, Ordered {

    /** 每分钟最大登录尝试次数(固定窗口,宽松值,正常测试不会触发) */
    private static final long MAX_PER_MINUTE = 30;
    /** 限流窗口(秒) */
    private static final long WINDOW_SECONDS = 60;
    /** 限流键前缀 */
    private static final String KEY_PREFIX = "gateway:login:limit:";

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 仅拦截登录接口
        if (exchange.getRequest().getMethod() != HttpMethod.POST) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();
        if (!"/user/login".equals(path) && !"/api/user/users/login".equals(path)) {
            return chain.filter(exchange);
        }

        String ip = exchange.getRequest().getRemoteAddress() == null
                || exchange.getRequest().getRemoteAddress().getAddress() == null
                ? "unknown" : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        String key = KEY_PREFIX + ip;

        // Redis 计数 + 窗口设置;Redis 故障时降级为"本次不计数直接放行",不阻断登录。
        // 注意:onErrorResume 只包住 Redis 操作,不能包住后面的 chain.filter,
        // 否则下游路由/服务异常会被误吞成"限流异常"并触发二次转发。
        Mono<Long> countMono = redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    // 窗口内第一次计数时设置过期,形成固定窗口
                    if (count != null && count == 1L) {
                        return redisTemplate.expire(key, Duration.ofSeconds(WINDOW_SECONDS))
                                .thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .onErrorResume(e -> {
                    log.warn("登录限流 Redis 异常,本次不计数直接放行,原因:{}", e.getMessage());
                    return Mono.just(-1L);
                });

        return countMono.flatMap(count -> {
            if (count != null && count > MAX_PER_MINUTE) {
                log.warn("登录限流触发, ip={}, count={}", ip, count);
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        });
    }

    @Override
    public int getOrder() {
        return -10;
    }
}
