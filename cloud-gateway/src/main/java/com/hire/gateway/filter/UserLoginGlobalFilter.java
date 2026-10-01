package com.hire.gateway.filter;

import com.hire.common.constant.JwtConstant;
import com.hire.common.utils.JwtRequestUtils;
import com.hire.gateway.props.CloudProperties;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.nio.charset.StandardCharsets;

//全局过滤器默认就拦截所有路由,不需要做任何配置
@Component
@Slf4j
public class UserLoginGlobalFilter implements GlobalFilter, Ordered {
    @Autowired
    private CloudProperties cloudProperties;

    private AntPathMatcher antPathMatcher = new AntPathMatcher();

    //请求中应该携带jwt令牌,假设请求头的名称叫做Authorization: token  有一些路径是不需要校验,直接放行
    //我们通常在配置文件中进行配置白名单  http://localhost:10086/user/1
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Sanitize before the whitelist too: these headers may only originate at the gateway.
        exchange = exchange.mutate().request(exchange.getRequest().mutate().headers(headers -> {
            headers.remove("userId");
            headers.remove("role");
            headers.remove("X-User-Id");
        }).build()).build();
        //1.获取请求路径
        String requestPath = exchange.getRequest().getPath().value();
        log.info("requestPath={}", requestPath); // /user/1
        //2.判断该路径是否需要拦截,如果不需要拦截直接放行
        //像/user/**这种路径我们叫做antPath
        List<String> whitePaths = cloudProperties.getWhitePaths();
        if (whitePaths != null) {
            for (String whitePath : whitePaths) {
                if (antPathMatcher.match(whitePath, requestPath)) {
                    //白名单路径,直接放行
                    return chain.filter(exchange);
                }
            }
        }
        //3.获取请求头中的令牌
        //请求头中如何携带多个值 方式1: accept: aaaa,bbb  方式2 accept: aaa  accept:bbb
        try {
            boolean websocket = "/api/chat/ws".equals(requestPath)
                    && "websocket".equalsIgnoreCase(exchange.getRequest().getHeaders().getUpgrade());
            String token = JwtRequestUtils.token(exchange.getRequest().getHeaders().getFirst("Authorization"),
                    exchange.getRequest().getHeaders().get("Sec-WebSocket-Protocol"), websocket);
            Claims claims = JwtRequestUtils.parse(token);
            Long userId = JwtRequestUtils.userId(claims);
            log.info("userId={}", userId);
            //将userId放到请求头中,然后再放行转发给微服务
            ServerHttpRequest.Builder builder = exchange.getRequest().mutate()
                    .header("userId", userId.toString());
            //把 token 中的角色一并透传，微服务可直接做角色校验，无需再远程查用户服务
            Object role = claims.get(JwtConstant.ROLE);
            if (role != null) {
                builder.header("role", role.toString());
            }
            ServerHttpRequest newRequest = builder.build();
            //5.放行(必须使用mutate后的exchange,否则新请求头不会生效)
            return chain.filter(exchange.mutate().request(newRequest).build());
        } catch (Exception e) {
            log.debug("拒绝无效的登录凭证");
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED); //响应401
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] body = "{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}".getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        }
    }

    //返回的数值越小,顺序越靠前
    @Override
    public int getOrder() {
        return 0;
    }
}
