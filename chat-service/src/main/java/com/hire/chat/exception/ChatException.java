package com.hire.chat.exception;

public class ChatException extends RuntimeException {
    private final int code;

    public ChatException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static void require(boolean valid, int code, String message) {
        if (!valid) throw new ChatException(code, message);
    }
}
