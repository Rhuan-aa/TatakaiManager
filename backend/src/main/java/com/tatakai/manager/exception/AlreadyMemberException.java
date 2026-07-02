package com.tatakai.manager.exception;

public class AlreadyMemberException extends RuntimeException {
    public AlreadyMemberException() {
        super("Usuário já é membro desta campanha");
    }
}
