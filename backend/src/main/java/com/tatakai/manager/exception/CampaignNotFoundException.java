package com.tatakai.manager.exception;

public class CampaignNotFoundException extends RuntimeException {
    public CampaignNotFoundException() {
        super("Campanha não encontrada");
    }
}
