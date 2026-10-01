package com.hire.application.handler;

import com.hire.common.Result;
import com.hire.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 业务异常：直接把提示信息返回给前端
     */
    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        return Result.error(e.getMessage());
    }

    /**
     * 唯一索引冲突：并发下同一用户对同一职位重复投递的最终兜底（uk_user_job）
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("唯一索引冲突: {}", e.getMessage());
        return Result.error("已投递过该职位，请勿重复投递");
    }

    /**
     * 路径变量/请求参数类型不匹配（例如把非数字当作 id 传入）
     * 属于客户端请求问题，不应笼统地返回“服务器异常”
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public Result handleArgumentException(Exception e) {
        log.warn("请求参数不合法: {}", e.getMessage());
        return Result.error("请求参数不合法");
    }

    /**
     * 请求方法不匹配（例如用 GET 调 POST 接口）：属于客户端调用方式错误，返回明确提示
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return Result.error("请求方法不支持，请检查请求方式（如应使用 POST/PUT）");
    }

    /**
     * 兜底异常：记录日志，避免把堆栈暴露给前端。
     * 注意：@GlobalTransactional 方法抛出的 BusinessException 会被 Seata 包成 RuntimeException，
     * 这里需要沿 cause 链解包，把真实的业务提示返回给前端，而不是笼统的“服务器异常”
     */
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        BusinessException business = findBusinessException(e);
        if (business != null) {
            log.warn("业务异常(被包装): {}", business.getMessage());
            return Result.error(business.getMessage());
        }
        log.error("服务器异常", e);
        return Result.error("服务器异常，请稍后重试");
    }

    /**
     * 沿异常 cause 链查找被包装的 BusinessException
     */
    private BusinessException findBusinessException(Throwable e) {
        Throwable current = e;
        int depth = 0;
        while (current != null && depth < 10) {
            if (current instanceof BusinessException) {
                return (BusinessException) current;
            }
            current = current.getCause();
            depth++;
        }
        return null;
    }
}
