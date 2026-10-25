package com.tatakai.manager.controller;

import com.tatakai.manager.dto.request.CampaignRollRequest;
import com.tatakai.manager.dto.response.RollMessage;
import com.tatakai.manager.entity.RollChannel;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.RollService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/campaigns/{campaignId}/rolls")
public class CampaignRollController {

    private final RollService rollService;

    public CampaignRollController(RollService rollService) {
        this.rollService = rollService;
    }

    @GetMapping
    public ResponseEntity<List<RollMessage>> history(@PathVariable UUID campaignId,
                                                     @RequestParam(required = false) RollChannel channel,
                                                     @RequestParam(required = false) UUID customChannelId) {
        return ResponseEntity.ok(rollService.history(campaignId, channel, customChannelId, AuthenticatedUser.id()));
    }

    @PostMapping
    public ResponseEntity<RollMessage> gmRoll(@PathVariable UUID campaignId,
                                              @Valid @RequestBody CampaignRollRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rollService.campaignRoll(campaignId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/{rollId}/reveal")
    public ResponseEntity<RollMessage> reveal(@PathVariable UUID campaignId, @PathVariable UUID rollId) {
        return ResponseEntity.ok(rollService.reveal(campaignId, rollId, AuthenticatedUser.id()));
    }
}
