package com.tatakai.manager.campaign.controller;

import com.tatakai.manager.campaign.service.CampaignDeletionService;
import com.tatakai.manager.shared.security.AuthenticatedUser;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class CampaignDeletionController {

    private final CampaignDeletionService service;

    public CampaignDeletionController(CampaignDeletionService service) {
        this.service = service;
    }

    @DeleteMapping("/campaigns/{campaignId}")
    public ResponseEntity<Void> delete(@PathVariable UUID campaignId) {
        service.delete(campaignId, AuthenticatedUser.id());
        return ResponseEntity.noContent().build();
    }
}
