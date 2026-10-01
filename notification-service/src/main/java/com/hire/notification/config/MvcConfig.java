package com.hire.notification.config;

import com.hire.common.interceptor.UserInfoInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 配置：注册用户信息拦截器。
 *
 * 网关解析 JWT 后把 userId / role 放入请求头透传，
 * 本拦截器将其写入 UserContext(ThreadLocal)，供 Controller 获取当前登录用户，
 * 与 job-service 的做法保持一致。
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserInfoInterceptor()).addPathPatterns("/notifications/**");
    }
}
