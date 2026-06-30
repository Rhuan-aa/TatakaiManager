package com.tatakai.manager.controller;

import com.tatakai.manager.dto.request.CreateNpcRequest;
import com.tatakai.manager.dto.request.SetVisibilityRequest;
import com.tatakai.manager.dto.request.UpdateNpcRequest;
import com.tatakai.manager.dto.response.CampaignNpcResponse;
import com.tatakai.manager.dto.response.NpcResponse;
import com.tatakai.manager.dto.response.NpcSummaryResponse;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.NpcService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class NpcController {

    private final NpcService npcService;

    public NpcController(NpcService npcService) {
        this.npcService = npcService;
    }

    // ---- Acervo do Mestre (US-06, US-07) ----

    @PostMapping("/npcs")
    public ResponseEntity<NpcResponse> create(@Valid @RequestBody CreateNpcRequest request) {
        NpcResponse res = npcService.create(AuthenticatedUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @PutMapping("/npcs/{npcId}")
    public ResponseEntity<NpcResponse> update(@PathVariable UUID npcId,
                                              @Valid @RequestBody UpdateNpcRequest request) {
        return ResponseEntity.ok(npcService.update(npcId, AuthenticatedUser.id(), request));
    }

    // ---- Associação e visibilidade por campanha (US-08, US-20) ----

    @PostMapping("/campaigns/{campaignId}/npcs/{npcId}")
    public ResponseEntity<CampaignNpcResponse> associate(@PathVariable UUID campaignId,
                                                         @PathVariable UUID npcId) {
        CampaignNpcResponse res = npcService.associate(npcId, campaignId, AuthenticatedUser.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @PatchMapping("/campaigns/{campaignId}/npcs/{npcId}/visibility")
    public ResponseEntity<CampaignNpcResponse> setVisibility(@PathVariable UUID campaignId,
                                                            @PathVariable UUID npcId,
                                                            @Valid @RequestBody SetVisibilityRequest request) {
        CampaignNpcResponse res = npcService.setVisibility(
                campaignId, npcId, AuthenticatedUser.id(), request.visible());
        return ResponseEntity.ok(res);
    }

    @GetMapping("/campaigns/{campaignId}/npcs")
    public ResponseEntity<List<NpcSummaryResponse>> listForCampaign(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(npcService.listForCampaign(campaignId, AuthenticatedUser.id()));
    }

    @GetMapping("/campaigns/{campaignId}/npcs/{npcId}")
    public ResponseEntity<NpcResponse> getForCampaign(@PathVariable UUID campaignId,
                                                      @PathVariable UUID npcId) {
        return ResponseEntity.ok(npcService.getForCampaign(campaignId, npcId, AuthenticatedUser.id()));
    }
}
