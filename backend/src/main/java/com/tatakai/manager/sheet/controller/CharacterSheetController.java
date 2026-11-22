package com.tatakai.manager.sheet.controller;

import com.tatakai.manager.roll.dto.request.RollRequest;
import com.tatakai.manager.roll.dto.request.RollTestRequest;
import com.tatakai.manager.roll.dto.response.RollMessage;
import com.tatakai.manager.roll.dto.response.RollProfileResponse;
import com.tatakai.manager.roll.service.RollService;
import com.tatakai.manager.shared.security.AuthenticatedUser;
import com.tatakai.manager.sheet.dto.request.AddWoundRequest;
import com.tatakai.manager.sheet.dto.request.ApproveCharacterSheetRequest;
import com.tatakai.manager.sheet.dto.request.CreateCharacterSheetRequest;
import com.tatakai.manager.sheet.dto.request.CreateGmSheetRequest;
import com.tatakai.manager.sheet.dto.request.GmAdjustmentRequest;
import com.tatakai.manager.sheet.dto.request.SetNpcSectionVisibilityRequest;
import com.tatakai.manager.sheet.dto.request.SetNpcTrackingRequest;
import com.tatakai.manager.sheet.dto.request.UpdateCharacterSheetRequest;
import com.tatakai.manager.sheet.dto.request.UpdateResourcesRequest;
import com.tatakai.manager.sheet.dto.request.UseConsumableRequest;
import com.tatakai.manager.sheet.dto.response.CharacterSheetResponse;
import com.tatakai.manager.sheet.dto.response.CharacterSheetSummaryResponse;
import com.tatakai.manager.sheet.dto.response.NpcSectionVisibilityResponse;
import com.tatakai.manager.sheet.service.CharacterSheetService;

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
    private final com.tatakai.manager.sheet.service.RandomSheetService randomSheetService;
    private final com.tatakai.manager.martial.service.CharacterTechniqueService techniqueService;

    public CharacterSheetController(CharacterSheetService sheetService, RollService rollService,
                                    com.tatakai.manager.sheet.service.RandomSheetService randomSheetService,
                                    com.tatakai.manager.martial.service.CharacterTechniqueService techniqueService) {
        this.techniqueService = techniqueService;
        this.randomSheetService = randomSheetService;
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

    @PostMapping("/campaigns/{campaignId}/gm-sheets/random")
    public ResponseEntity<CharacterSheetResponse> createRandomGmSheet(
            @PathVariable UUID campaignId,
            @Valid @RequestBody com.tatakai.manager.sheet.dto.request.RandomSheetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(randomSheetService.create(campaignId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/campaigns/{campaignId}/gm-sheets")
    public ResponseEntity<CharacterSheetResponse> createGmSheet(@PathVariable UUID campaignId,
                                                                @Valid @RequestBody CreateGmSheetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sheetService.createGmSheet(campaignId, request, AuthenticatedUser.id()));
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

    @PostMapping("/character-sheets/{sheetId}/wounds")
    public ResponseEntity<CharacterSheetResponse> addWound(@PathVariable UUID sheetId,
                                                           @Valid @RequestBody AddWoundRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sheetService.addWound(sheetId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/character-sheets/{sheetId}/inventory/{itemId}/use")
    public ResponseEntity<CharacterSheetResponse> useConsumable(@PathVariable UUID sheetId, @PathVariable UUID itemId,
                                                                @Valid @RequestBody(required = false) UseConsumableRequest request) {
        return ResponseEntity.ok(sheetService.useConsumable(sheetId, itemId, request == null ? null : request.option(),
                AuthenticatedUser.id()));
    }

    @DeleteMapping("/character-sheets/{sheetId}/wounds/{woundId}")
    public ResponseEntity<CharacterSheetResponse> removeWound(@PathVariable UUID sheetId, @PathVariable UUID woundId) {
        return ResponseEntity.ok(sheetService.removeWound(sheetId, woundId, AuthenticatedUser.id()));
    }

    @PatchMapping("/npcs/{npcId}/sheet/tracking")
    public ResponseEntity<CharacterSheetResponse> setNpcTracking(@PathVariable UUID npcId,
                                                                 @Valid @RequestBody SetNpcTrackingRequest request) {
        return ResponseEntity.ok(sheetService.setNpcTracking(npcId, request.enabled(), AuthenticatedUser.id()));
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

    @GetMapping("/campaigns/{campaignId}/npcs/{npcId}/sheet/martial")
    public ResponseEntity<com.tatakai.manager.martial.dto.response.SheetMartialResponse> getNpcMartial(
            @PathVariable UUID campaignId, @PathVariable UUID npcId) {
        return ResponseEntity.ok(techniqueService.getNpcMartialInCampaign(campaignId, npcId, AuthenticatedUser.id()));
    }

    @PutMapping("/campaigns/{campaignId}/npcs/{npcId}/sheet/visibility")
    public ResponseEntity<List<NpcSectionVisibilityResponse>> updateNpcSheetVisibility(@PathVariable UUID campaignId,
                                                                                      @PathVariable UUID npcId,
                                                                                      @Valid @RequestBody com.tatakai.manager.sheet.dto.request.UpdateNpcSheetVisibilityRequest request) {
        return ResponseEntity.ok(sheetService.updateNpcSheetVisibility(campaignId, npcId, request, AuthenticatedUser.id()));
    }
}
