package com.tatakai.manager.campaign.exception;

public class AlreadyMemberException extends RuntimeException {
    public AlreadyMemberException() {
        super("Usuário já é membro desta campanha");
    }
}
