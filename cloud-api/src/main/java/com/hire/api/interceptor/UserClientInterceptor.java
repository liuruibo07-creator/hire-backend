package com.hire.api.interceptor;

import com.hire.common.context.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;

//OpenFeign请求拦截器
public class UserClientInterceptor implements RequestInterceptor {
    /**
     * 在OpenFeign请求之前，将当前用户的ID设置到请求头中
     * @param template
     */
    @Override
    public void apply(RequestTemplate template) {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId != null) {
            //给远程请求添加一个UserId的头
            template.header("userId", currentUserId.toString());
        }
        //透传角色,否则目标服务的管理员接口(如 /admin/jobs/**)会因 role 缺失而 403
        String currentRole = UserContext.getCurrentRole();
        if (currentRole != null && !currentRole.isEmpty()) {
            template.header("role", currentRole);
        }
    }
}
