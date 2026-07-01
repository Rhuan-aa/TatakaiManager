package com.tatakai.manager.exception;

public class MemberNotFoundException extends RuntimeException {
    public MemberNotFoundException() {
        super("Membro não encontrado nesta campanha");
    }
}
