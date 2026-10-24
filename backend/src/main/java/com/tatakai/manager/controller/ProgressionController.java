package com.tatakai.manager.controller;

import com.tatakai.manager.dto.request.FightRequest;
import com.tatakai.manager.dto.request.InstinctChoiceRequest;
import com.tatakai.manager.dto.request.OvercomeRequest;
import com.tatakai.manager.dto.request.ReviewArtRequest;
import com.tatakai.manager.dto.request.RivalRequest;
import com.tatakai.manager.dto.response.SheetProgressionResponse;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.ProgressionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class ProgressionController {

    private final ProgressionService service;

    public ProgressionController(ProgressionService service) {
        this.service = service;
    }

    @PostMapping("/campaigns/{campaignId}/fights")
    public ResponseEntity<List<SheetProgressionResponse>> recordFight(@PathVariable UUID campaignId,
                                                                      @Valid @RequestBody FightRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.recordFight(campaignId, request, AuthenticatedUser.id()));
    }

    @GetMapping("/character-sheets/{sheetId}/progression")
    public ResponseEntity<SheetProgressionResponse> get(@PathVariable UUID sheetId) {
        return ResponseEntity.ok(service.get(sheetId, AuthenticatedUser.id()));
    }

    @PostMapping("/character-sheets/{sheetId}/overcome")
    public ResponseEntity<SheetProgressionResponse> requestOvercome(@PathVariable UUID sheetId,
                                                                    @Valid @RequestBody OvercomeRequest request) {
        return ResponseEntity.ok(service.requestOvercome(sheetId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/character-sheets/{sheetId}/overcome/{distributionId}/review")
    public ResponseEntity<SheetProgressionResponse> reviewOvercome(@PathVariable UUID sheetId,
                                                                   @PathVariable UUID distributionId,
                                                                   @Valid @RequestBody ReviewArtRequest request) {
        return ResponseEntity.ok(service.reviewOvercome(sheetId, distributionId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/character-sheets/{sheetId}/instincts")
    public ResponseEntity<SheetProgressionResponse> chooseInstinct(@PathVariable UUID sheetId,
                                                                   @Valid @RequestBody InstinctChoiceRequest request) {
        return ResponseEntity.ok(service.chooseInstinct(sheetId, request, AuthenticatedUser.id()));
    }

    @PutMapping("/character-sheets/{sheetId}/rivals/{rivalId}")
    public ResponseEntity<SheetProgressionResponse> updateRival(@PathVariable UUID sheetId, @PathVariable UUID rivalId,
                                                                @Valid @RequestBody RivalRequest request) {
        return ResponseEntity.ok(service.updateRival(sheetId, rivalId, request, AuthenticatedUser.id()));
    }

    @DeleteMapping("/character-sheets/{sheetId}/rivals/{rivalId}")
    public ResponseEntity<SheetProgressionResponse> removeRival(@PathVariable UUID sheetId, @PathVariable UUID rivalId) {
        return ResponseEntity.ok(service.removeRival(sheetId, rivalId, AuthenticatedUser.id()));
    }

    @DeleteMapping("/character-sheets/{sheetId}/instincts/{instinctId}")
    public ResponseEntity<SheetProgressionResponse> removeInstinct(@PathVariable UUID sheetId,
                                                                   @PathVariable UUID instinctId) {
        return ResponseEntity.ok(service.removeInstinct(sheetId, instinctId, AuthenticatedUser.id()));
    }
}
