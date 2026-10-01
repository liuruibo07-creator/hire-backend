package com.hire.user.handler;
import com.hire.user.exception.UserApiException;
import com.hire.user.model.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(UserApiException.class)
    public ApiResult<Void> business(UserApiException e) { return ApiResult.of(e.getCode(),e.getMessage(),null); }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ApiResult<Void> parameter(Exception e) { return ApiResult.of(400,"请求参数不合法",null); }
    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResult<Void> conflict(DuplicateKeyException e) { return ApiResult.of(409,"用户名或邮箱已存在",null); }
    @ExceptionHandler(Exception.class)
    public ApiResult<Void> unexpected(Exception e) {
        log.error("用户服务异常",e); return ApiResult.of(500,"服务器内部错误",null);
    }
}
