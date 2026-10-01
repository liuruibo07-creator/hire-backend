package com.hire.job.config;

import com.hire.common.interceptor.UserInfoInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC配置:注册用户信息拦截器
 * 从请求头 userId 提取当前登录用户ID,写入 UserContext(ThreadLocal)
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserInfoInterceptor())
                .addPathPatterns("/api/**", "/jobs/**", "/admin/**");
    }
}