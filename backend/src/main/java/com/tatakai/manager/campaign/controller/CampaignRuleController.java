package com.tatakai.manager.campaign.controller;

import com.tatakai.manager.campaign.dto.request.UpdateCampaignRuleRequest;
import com.tatakai.manager.campaign.dto.response.CampaignRuleResponse;
import com.tatakai.manager.campaign.entity.OptionalRule;
import com.tatakai.manager.campaign.service.CampaignRuleService;
import com.tatakai.manager.shared.security.AuthenticatedUser;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class CampaignRuleController {

    private final CampaignRuleService ruleService;

    public CampaignRuleController(CampaignRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping("/campaigns/{campaignId}/rules")
    public ResponseEntity<List<CampaignRuleResponse>> list(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(ruleService.list(campaignId, AuthenticatedUser.id()));
    }

    @PutMapping("/campaigns/{campaignId}/rules/{ruleKey}")
    public ResponseEntity<CampaignRuleResponse> update(@PathVariable UUID campaignId,
                                                       @PathVariable OptionalRule ruleKey,
                                                       @Valid @RequestBody UpdateCampaignRuleRequest request) {
        return ResponseEntity.ok(ruleService.update(campaignId, ruleKey, request, AuthenticatedUser.id()));
    }
}
