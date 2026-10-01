package com.hire.chat.exception;

import com.hire.common.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Slf4j
public class ChatExceptionHandler {
    @ExceptionHandler(ChatException.class)
    public ResponseEntity<Result<Void>> business(ChatException e) {
        return ResponseEntity.status(e.getCode()).body(Result.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<Result<Void>> invalid(Exception e) {
        return ResponseEntity.badRequest().body(Result.error(400, "请求参数不合法"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> unexpected(Exception e) {
        log.error("聊天服务异常", e);
        return ResponseEntity.status(500).body(Result.error(500, "服务器内部错误"));
    }
}
