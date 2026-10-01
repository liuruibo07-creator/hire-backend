package com.hire.api.conf;

import com.hire.api.interceptor.UserClientInterceptor;
import feign.Logger;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class DefaultFeignConfig {

    //Logger.Level.FULL 这个枚举项 表示显示所有的请求和响应报文信息
    @Bean
    public Logger.Level feignLogLevel() {
        return Logger.Level.FULL;
    }

    //配置拦截器,通过bean注解注册到ioc容器中
    @Bean
    public RequestInterceptor requestInterceptor() {
        return new UserClientInterceptor();
    }


}
