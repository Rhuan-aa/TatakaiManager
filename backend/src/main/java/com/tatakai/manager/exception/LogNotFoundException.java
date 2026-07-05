package com.tatakai.manager.exception;

public class LogNotFoundException extends RuntimeException {
    public LogNotFoundException() {
        super("Log não encontrado");
    }
}
