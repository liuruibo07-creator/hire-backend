package com.hire.common.context;
/**
 * 用户上下文，用于存放当前登录用户的信息
 */
public class UserContext {

    //ThreadLocal可以理解成就是一个容器.<Long> 存放到容器中的数据类型
    public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    //存放当前登录用户的角色，由网关解析 token 后透传，避免每次校验角色都远程调用用户服务
    private static ThreadLocal<String> roleThreadLocal = new ThreadLocal<>();

    public static void setCurrentUserId(Long id) {
        threadLocal.set(id);
    }

    public static Long getCurrentUserId() {
        return threadLocal.get();
    }

    public static void setCurrentRole(String role) {
        roleThreadLocal.set(role);
    }

    public static String getCurrentRole() {
        return roleThreadLocal.get();
    }

    /**
     * 清理当前线程的用户信息，防止线程池复用导致数据串号或内存泄漏（同时清理 userId 与 role）
     */
    public static void removeCurrentUserId() {
        threadLocal.remove();
        roleThreadLocal.remove();
    }

}
