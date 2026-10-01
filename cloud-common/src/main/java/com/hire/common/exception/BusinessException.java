package com.hire.common.exception;

//业务异常：用于登录失败、参数不合法等可预期错误，由全局异常处理器转成 Result.error 返回
public class BusinessException extends RuntimeException {

    public BusinessException() {
    }

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
