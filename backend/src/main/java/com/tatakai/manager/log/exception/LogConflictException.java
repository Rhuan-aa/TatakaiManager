package com.tatakai.manager.log.exception;

public class LogConflictException extends RuntimeException {
    public LogConflictException(String message) {
        super(message);
    }
}
