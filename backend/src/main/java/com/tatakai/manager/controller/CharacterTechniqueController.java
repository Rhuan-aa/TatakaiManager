package com.tatakai.manager.controller;

import com.tatakai.manager.dto.request.*;
import com.tatakai.manager.dto.response.SheetMartialResponse;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.CharacterTechniqueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/character-sheets/{sheetId}")
public class CharacterTechniqueController {

    private final CharacterTechniqueService service;
    private final com.tatakai.manager.service.TrainingService trainingService;

    public CharacterTechniqueController(CharacterTechniqueService service,
                                        com.tatakai.manager.service.TrainingService trainingService) {
        this.service = service;
        this.trainingService = trainingService;
    }

    @GetMapping("/training")
    public ResponseEntity<java.util.List<com.tatakai.manager.dto.response.TrainingEntryResponse>> training(
            @PathVariable UUID sheetId) {
        return ResponseEntity.ok(trainingService.list(sheetId, AuthenticatedUser.id()));
    }

    @GetMapping("/martial")
    public ResponseEntity<SheetMartialResponse> get(@PathVariable UUID sheetId) {
        return ResponseEntity.ok(service.get(sheetId, AuthenticatedUser.id()));
    }

    @PostMapping("/arts")
    public ResponseEntity<SheetMartialResponse> requestArt(@PathVariable UUID sheetId,
                                                           @Valid @RequestBody CharacterArtRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.requestArt(sheetId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/arts/{artId}/review")
    public ResponseEntity<SheetMartialResponse> reviewArt(@PathVariable UUID sheetId, @PathVariable UUID artId,
                                                          @Valid @RequestBody ReviewArtRequest request) {
        return ResponseEntity.ok(service.reviewArt(sheetId, artId, request, AuthenticatedUser.id()));
    }

    @PatchMapping("/arts/{artId}/graduation")
    public ResponseEntity<SheetMartialResponse> setGraduation(@PathVariable UUID sheetId, @PathVariable UUID artId,
                                                              @Valid @RequestBody GraduationRequest request) {
        return ResponseEntity.ok(service.setGraduation(sheetId, artId, request, AuthenticatedUser.id()));
    }

    @DeleteMapping("/arts/{artId}")
    public ResponseEntity<SheetMartialResponse> removeArt(@PathVariable UUID sheetId, @PathVariable UUID artId) {
        return ResponseEntity.ok(service.removeArt(sheetId, artId, AuthenticatedUser.id()));
    }

    @PostMapping("/techniques")
    public ResponseEntity<SheetMartialResponse> learn(@PathVariable UUID sheetId,
                                                      @Valid @RequestBody LearnMoveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.learn(sheetId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/techniques/{techniqueId}/review")
    public ResponseEntity<SheetMartialResponse> reviewTechnique(@PathVariable UUID sheetId, @PathVariable UUID techniqueId,
                                                                @Valid @RequestBody ReviewArtRequest request) {
        return ResponseEntity.ok(service.reviewTechnique(sheetId, techniqueId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/techniques/{techniqueId}/training")
    public ResponseEntity<SheetMartialResponse> train(@PathVariable UUID sheetId, @PathVariable UUID techniqueId,
                                                      @Valid @RequestBody PointsRequest request) {
        return ResponseEntity.ok(service.train(sheetId, techniqueId, request, AuthenticatedUser.id()));
    }

    @PatchMapping("/techniques/{techniqueId}/state")
    public ResponseEntity<SheetMartialResponse> setState(@PathVariable UUID sheetId, @PathVariable UUID techniqueId,
                                                         @Valid @RequestBody TechniqueStateRequest request) {
        return ResponseEntity.ok(service.setState(sheetId, techniqueId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/techniques/{techniqueId}/mastery-points")
    public ResponseEntity<SheetMartialResponse> masteryPoints(@PathVariable UUID sheetId, @PathVariable UUID techniqueId,
                                                              @Valid @RequestBody PointsRequest request) {
        return ResponseEntity.ok(service.addMasteryPoints(sheetId, techniqueId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/techniques/{techniqueId}/level-up")
    public ResponseEntity<SheetMartialResponse> levelUp(@PathVariable UUID sheetId, @PathVariable UUID techniqueId,
                                                        @Valid @RequestBody LevelUpRequest request) {
        return ResponseEntity.ok(service.levelUp(sheetId, techniqueId, request, AuthenticatedUser.id()));
    }

    @PatchMapping("/techniques/{techniqueId}/mastery")
    public ResponseEntity<SheetMartialResponse> setMastery(@PathVariable UUID sheetId, @PathVariable UUID techniqueId,
                                                           @Valid @RequestBody MasteryRequest request) {
        return ResponseEntity.ok(service.setMastery(sheetId, techniqueId, request, AuthenticatedUser.id()));
    }

    @DeleteMapping("/techniques/{techniqueId}")
    public ResponseEntity<SheetMartialResponse> removeTechnique(@PathVariable UUID sheetId, @PathVariable UUID techniqueId) {
        return ResponseEntity.ok(service.removeTechnique(sheetId, techniqueId, AuthenticatedUser.id()));
    }
}
