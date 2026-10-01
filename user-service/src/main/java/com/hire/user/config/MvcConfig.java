package com.hire.user.config;
import com.hire.common.context.UserContext;
import com.hire.user.exception.UserApiException;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;
import javax.servlet.http.*;
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
                UserContext.removeCurrentUserId();
                String id=request.getHeader("X-User-Id");
                // Retain compatibility with the existing gateway without changing its module.
                if(id==null) id=request.getHeader("userId");
                if(id!=null) {
                    try {
                        long value=Long.parseLong(id);
                        if(value<=0) throw new NumberFormatException();
                        UserContext.setCurrentUserId(value);
                    } catch(NumberFormatException e) { throw new UserApiException(401,"登录信息不合法"); }
                }
                return true;
            }
            @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception ex) {
                UserContext.removeCurrentUserId();
            }
        });
    }
}
