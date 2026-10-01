package com.hire.common.interceptor;

import cn.hutool.core.util.StrUtil;
import com.hire.common.context.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class UserInfoInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取userId请求头
        String userId = request.getHeader("userId");
        //2.存放到ThreadLocal中（去除可能误带的引号和空白，解析失败则忽略，避免整个请求报服务器异常）
        if (StrUtil.isNotBlank(userId)) {
            String cleaned = userId.trim();
            if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
            }
            try {
                UserContext.setCurrentUserId(Long.valueOf(cleaned));
            } catch (NumberFormatException e) {
                log.warn("非法的userId请求头: {}", userId);
            }
        }
        //3.获取网关透传的角色请求头（来源于 token 中的 role claim）
        String role = request.getHeader("role");
        if (StrUtil.isNotBlank(role)) {
            UserContext.setCurrentRole(role);
        }
        //4.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //删除ThreadLocal中的数据防止OOM
        UserContext.removeCurrentUserId();
    }
}
