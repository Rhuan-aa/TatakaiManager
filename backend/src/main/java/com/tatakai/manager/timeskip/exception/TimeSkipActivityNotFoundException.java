package com.tatakai.manager.timeskip.exception;

public class TimeSkipActivityNotFoundException extends RuntimeException {
    public TimeSkipActivityNotFoundException() {
        super("Atividade solo não encontrada");
    }
}
