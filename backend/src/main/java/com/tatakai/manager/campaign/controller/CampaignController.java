package com.tatakai.manager.campaign.controller;

import com.tatakai.manager.campaign.dto.request.CreateCampaignRequest;
import com.tatakai.manager.campaign.dto.request.InviteMemberRequest;
import com.tatakai.manager.campaign.dto.response.CampaignResponse;
import com.tatakai.manager.campaign.dto.response.MemberResponse;
import com.tatakai.manager.campaign.service.CampaignService;
import com.tatakai.manager.shared.security.AuthenticatedUser;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping
    public ResponseEntity<CampaignResponse> create(@Valid @RequestBody CreateCampaignRequest request) {
        CampaignResponse res = campaignService.create(AuthenticatedUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping
    public ResponseEntity<List<CampaignResponse>> myCampaigns() {
        return ResponseEntity.ok(campaignService.listMyCampaigns(AuthenticatedUser.id()));
    }

    @GetMapping("/{campaignId}/members")
    public ResponseEntity<List<MemberResponse>> members(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.listMembers(campaignId, AuthenticatedUser.id()));
    }

    @PostMapping("/{campaignId}/members")
    public ResponseEntity<MemberResponse> invite(@PathVariable UUID campaignId,
                                                 @Valid @RequestBody InviteMemberRequest request) {
        MemberResponse res = campaignService.invite(campaignId, AuthenticatedUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }
}
