package com.hire.user.exception;
public class UserApiException extends RuntimeException {
    private final int code;
    public UserApiException(int code, String message) { super(message); this.code = code; }
    public int getCode() { return code; }
}
