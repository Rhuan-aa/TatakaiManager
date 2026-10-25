package com.tatakai.manager.log.exception;

public class LogNotFoundException extends RuntimeException {
    public LogNotFoundException() {
        super("Log não encontrado");
    }
}
