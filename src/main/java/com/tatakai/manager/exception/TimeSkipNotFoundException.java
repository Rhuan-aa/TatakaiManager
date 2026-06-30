package com.tatakai.manager.exception;

public class TimeSkipNotFoundException extends RuntimeException {
    public TimeSkipNotFoundException() {
        super("TimeSkip não encontrado");
    }
}
