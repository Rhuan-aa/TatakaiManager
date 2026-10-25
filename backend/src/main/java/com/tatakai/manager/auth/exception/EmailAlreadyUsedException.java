package com.tatakai.manager.auth.exception;

public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException(String email) {
        super("E-mail já cadastrado: " + email);
    }
}
