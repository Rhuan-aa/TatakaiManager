package com.tatakai.manager.exception;

public class InvalidActivityNameException extends RuntimeException {
    public InvalidActivityNameException() {
        super("Já existe uma atividade com esse nome neste TimeSkip");
    }
}
