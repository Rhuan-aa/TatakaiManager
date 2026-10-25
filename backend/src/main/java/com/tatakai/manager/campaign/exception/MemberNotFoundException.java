package com.tatakai.manager.campaign.exception;

public class MemberNotFoundException extends RuntimeException {
    public MemberNotFoundException() {
        super("Membro não encontrado nesta campanha");
    }
}
