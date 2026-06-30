package com.tatakai.manager.exception;

public class TimeSkipClosedException extends RuntimeException {
    public TimeSkipClosedException() {
        super("Este TimeSkip já está encerrado");
    }
}
