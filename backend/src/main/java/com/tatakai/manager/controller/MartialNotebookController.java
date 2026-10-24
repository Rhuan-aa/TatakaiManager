package com.tatakai.manager.controller;

import com.tatakai.manager.dto.request.ForgeStartRequest;
import com.tatakai.manager.dto.request.NotebookDraftRequest;
import com.tatakai.manager.dto.request.ReviewArtRequest;
import com.tatakai.manager.dto.response.NotebookResponse;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.MartialNotebookService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/character-sheets/{sheetId}")
public class MartialNotebookController {

    private final MartialNotebookService service;

    public MartialNotebookController(MartialNotebookService service) {
        this.service = service;
    }

    @GetMapping("/notebook")
    public ResponseEntity<NotebookResponse> get(@PathVariable UUID sheetId) {
        return ResponseEntity.ok(service.get(sheetId, AuthenticatedUser.id()));
    }

    @PostMapping("/notebook")
    public ResponseEntity<NotebookResponse> create(@PathVariable UUID sheetId,
                                                   @Valid @RequestBody NotebookDraftRequest request) {
        return ResponseEntity.ok(service.create(sheetId, request, AuthenticatedUser.id()));
    }

    @PutMapping("/notebook/{draftId}")
    public ResponseEntity<NotebookResponse> update(@PathVariable UUID sheetId, @PathVariable UUID draftId,
                                                   @Valid @RequestBody NotebookDraftRequest request) {
        return ResponseEntity.ok(service.update(sheetId, draftId, request, AuthenticatedUser.id()));
    }

    @DeleteMapping("/notebook/{draftId}")
    public ResponseEntity<NotebookResponse> delete(@PathVariable UUID sheetId, @PathVariable UUID draftId) {
        return ResponseEntity.ok(service.delete(sheetId, draftId, AuthenticatedUser.id()));
    }

    @PostMapping("/notebook/{draftId}/submit")
    public ResponseEntity<NotebookResponse> submit(@PathVariable UUID sheetId, @PathVariable UUID draftId) {
        return ResponseEntity.ok(service.submit(sheetId, draftId, AuthenticatedUser.id()));
    }

    @PostMapping("/notebook/{draftId}/review")
    public ResponseEntity<NotebookResponse> review(@PathVariable UUID sheetId, @PathVariable UUID draftId,
                                                   @Valid @RequestBody ReviewArtRequest request) {
        return ResponseEntity.ok(service.review(sheetId, draftId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/forge")
    public ResponseEntity<NotebookResponse> startForge(@PathVariable UUID sheetId,
                                                       @Valid @RequestBody ForgeStartRequest request) {
        return ResponseEntity.ok(service.startForge(sheetId, request, AuthenticatedUser.id()));
    }

    @DeleteMapping("/forge")
    public ResponseEntity<NotebookResponse> abandonForge(@PathVariable UUID sheetId) {
        return ResponseEntity.ok(service.abandonForge(sheetId, AuthenticatedUser.id()));
    }
}
