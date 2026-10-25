package com.tatakai.manager.roll.exception;

public class RollNotFoundException extends RuntimeException {
    public RollNotFoundException() {
        super("Rolagem não encontrada");
    }

    public RollNotFoundException(String message) {
        super(message);
    }
}
