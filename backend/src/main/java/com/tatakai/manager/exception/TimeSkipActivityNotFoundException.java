package com.tatakai.manager.exception;

public class TimeSkipActivityNotFoundException extends RuntimeException {
    public TimeSkipActivityNotFoundException() {
        super("Atividade solo não encontrada");
    }
}
