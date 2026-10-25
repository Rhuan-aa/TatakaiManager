package com.tatakai.manager.campaign.exception;

public class CampaignNotFoundException extends RuntimeException {
    public CampaignNotFoundException() {
        super("Campanha não encontrada");
    }
}
