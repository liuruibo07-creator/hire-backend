package com.hire.common.constant;

public class JwtConstant {
    /**
     * 签名密钥，必须与网关 UserLoginGlobalFilter 中解析 token 使用的密钥保持一致
     */
    public static final String SECRET_KEY = loadSecret();

    private static String loadSecret() {
        String value = System.getProperty("hire.jwt.secret", System.getenv("JWT_SECRET"));
        if (value == null || value.length() < 32) {
            throw new IllegalStateException("Set JWT_SECRET (at least 32 characters) consistently for all services");
        }
        return value;
    }

    /**
     * token 有效期，单位毫秒，默认 24 小时
     */
    public static final long TTL_MILLIS = 1000L * 60 * 60 * 24;

    /**
     * payload 中存放用户id的key，网关就是通过这个key取userId的
     */
    public static final String USER_ID = "userId";

    /**
     * payload 中存放用户角色的key，微服务通过该key校验角色，避免每次都远程查询用户服务
     * 角色变更后 token 里的角色仍是旧值，需要重新登录（或刷新 token）才会生效
     */
    public static final String ROLE = "role";
}
