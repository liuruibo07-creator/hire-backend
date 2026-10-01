package com.hire.common.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结果封装
 * 所有接口返回该结构:{ code, message, data }
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int SUCCESS_CODE = 200;      // 操作成功
    public static final int BAD_REQUEST_CODE = 400;  // 请求参数错误
    public static final int UNAUTHORIZED_CODE = 401; // 未登录或令牌过期
    public static final int FORBIDDEN_CODE = 403;    // 无权限访问
    public static final int NOT_FOUND_CODE = 404;    // 资源不存在
    public static final int CONFLICT_CODE = 409;     // 资源冲突
    public static final int ERROR_CODE = 500;        // 服务器内部错误

    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        return build(SUCCESS_CODE, "操作成功", null);
    }

    public static <T> Result<T> success(T data) {
        return build(SUCCESS_CODE, "操作成功", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return build(SUCCESS_CODE, message, data);
    }

    public static <T> Result<T> error(String message) {
        return build(ERROR_CODE, message, null);
    }

    public static <T> Result<T> error(Integer code, String message) {
        return build(code, message, null);
    }

    private static <T> Result<T> build(Integer code, String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(data);
        return result;
    }
}