package com.tatakai.manager.exception;

public class RollNotFoundException extends RuntimeException {
    public RollNotFoundException() {
        super("Rolagem não encontrada");
    }

    public RollNotFoundException(String message) {
        super(message);
    }
}
