package com.tatakai.manager.controller;

import com.tatakai.manager.dto.request.*;
import com.tatakai.manager.dto.response.*;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.*;
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
