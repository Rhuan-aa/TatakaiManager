package com.tatakai.manager.controller;

import com.tatakai.manager.dto.request.*;
import com.tatakai.manager.dto.response.CharacterSheetResponse;
import com.tatakai.manager.dto.response.CharacterSheetSummaryResponse;
import com.tatakai.manager.dto.response.NpcSectionVisibilityResponse;
import com.tatakai.manager.dto.response.RollMessage;
import com.tatakai.manager.dto.response.RollProfileResponse;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.CharacterSheetService;
import com.tatakai.manager.service.RollService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class CharacterSheetController {

    private final CharacterSheetService sheetService;
    private final RollService rollService;

    public CharacterSheetController(CharacterSheetService sheetService, RollService rollService) {
        this.sheetService = sheetService;
        this.rollService = rollService;
    }

    @PostMapping("/campaigns/{campaignId}/character-sheets")
    public ResponseEntity<CharacterSheetResponse> create(@PathVariable UUID campaignId,
                                                         @Valid @RequestBody CreateCharacterSheetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sheetService.create(campaignId, request, AuthenticatedUser.id()));
    }

    @GetMapping("/campaigns/{campaignId}/character-sheets")
    public ResponseEntity<List<CharacterSheetSummaryResponse>> list(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(sheetService.listForCampaign(campaignId, AuthenticatedUser.id()));
    }

    @GetMapping("/character-sheets/{sheetId}")
    public ResponseEntity<CharacterSheetResponse> get(@PathVariable UUID sheetId) {
        return ResponseEntity.ok(sheetService.get(sheetId, AuthenticatedUser.id()));
    }

    @PutMapping("/character-sheets/{sheetId}")
    public ResponseEntity<CharacterSheetResponse> update(@PathVariable UUID sheetId,
                                                         @Valid @RequestBody UpdateCharacterSheetRequest request) {
        return ResponseEntity.ok(sheetService.update(sheetId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/character-sheets/{sheetId}/submit")
    public ResponseEntity<CharacterSheetResponse> submit(@PathVariable UUID sheetId) {
        return ResponseEntity.ok(sheetService.submit(sheetId, AuthenticatedUser.id()));
    }

    @PostMapping("/character-sheets/{sheetId}/approve")
    public ResponseEntity<CharacterSheetResponse> approve(@PathVariable UUID sheetId,
                                                          @Valid @RequestBody ApproveCharacterSheetRequest request) {
        return ResponseEntity.ok(sheetService.approve(sheetId, request, AuthenticatedUser.id()));
    }

    @PatchMapping("/character-sheets/{sheetId}/resources")
    public ResponseEntity<CharacterSheetResponse> updateResources(@PathVariable UUID sheetId,
                                                                  @Valid @RequestBody UpdateResourcesRequest request) {
        return ResponseEntity.ok(sheetService.updateResources(sheetId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/character-sheets/{sheetId}/gm-adjustments")
    public ResponseEntity<CharacterSheetResponse> gmAdjustment(@PathVariable UUID sheetId,
                                                               @Valid @RequestBody GmAdjustmentRequest request) {
        return ResponseEntity.ok(sheetService.applyGmAdjustment(sheetId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/character-sheets/{sheetId}/rolls")
    public ResponseEntity<RollMessage> roll(@PathVariable UUID sheetId,
                                            @Valid @RequestBody RollRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rollService.roll(sheetId, request, AuthenticatedUser.id()));
    }

    @GetMapping("/character-sheets/{sheetId}/roll-profiles")
    public ResponseEntity<List<RollProfileResponse>> rollProfiles(@PathVariable UUID sheetId) {
        return ResponseEntity.ok(rollService.profiles(sheetId, AuthenticatedUser.id()));
    }

    @PostMapping("/character-sheets/{sheetId}/rolls/test")
    public ResponseEntity<RollMessage> testRoll(@PathVariable UUID sheetId,
                                                @Valid @RequestBody RollTestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rollService.testRoll(sheetId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/npcs/{npcId}/sheet")
    public ResponseEntity<CharacterSheetResponse> createForNpc(@PathVariable UUID npcId,
                                                               @Valid @RequestBody CreateCharacterSheetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sheetService.createForNpc(npcId, request, AuthenticatedUser.id()));
    }

    @GetMapping("/npcs/{npcId}/sheet")
    public ResponseEntity<CharacterSheetResponse> getForNpc(@PathVariable UUID npcId) {
        return ResponseEntity.ok(sheetService.getForNpc(npcId, AuthenticatedUser.id()));
    }

    @GetMapping("/campaigns/{campaignId}/npcs/{npcId}/sheet")
    public ResponseEntity<CharacterSheetResponse> getNpcSheetInCampaign(@PathVariable UUID campaignId,
                                                                        @PathVariable UUID npcId) {
        return ResponseEntity.ok(sheetService.getNpcSheetInCampaign(campaignId, npcId, AuthenticatedUser.id()));
    }

    @GetMapping("/campaigns/{campaignId}/npcs/{npcId}/sheet/visibility")
    public ResponseEntity<List<NpcSectionVisibilityResponse>> listNpcSheetVisibility(@PathVariable UUID campaignId,
                                                                                    @PathVariable UUID npcId) {
        return ResponseEntity.ok(sheetService.listNpcSheetVisibility(campaignId, npcId, AuthenticatedUser.id()));
    }

    @PatchMapping("/campaigns/{campaignId}/npcs/{npcId}/sheet/visibility")
    public ResponseEntity<NpcSectionVisibilityResponse> setNpcSheetVisibility(@PathVariable UUID campaignId,
                                                                              @PathVariable UUID npcId,
                                                                              @Valid @RequestBody SetNpcSectionVisibilityRequest request) {
        return ResponseEntity.ok(sheetService.setNpcSheetVisibility(campaignId, npcId, request, AuthenticatedUser.id()));
    }
}
