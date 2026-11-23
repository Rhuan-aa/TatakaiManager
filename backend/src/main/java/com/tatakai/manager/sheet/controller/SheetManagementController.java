package com.tatakai.manager.sheet.controller;

import com.tatakai.manager.shared.security.AuthenticatedUser;
import com.tatakai.manager.sheet.dto.request.CreateChangeRequest;
import com.tatakai.manager.sheet.dto.request.ReviewChangeRequest;
import com.tatakai.manager.sheet.dto.response.ChangeRequestResponse;
import com.tatakai.manager.sheet.service.SheetManagementService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/character-sheets/{sheetId}")
public class SheetManagementController {

    private final SheetManagementService service;

    public SheetManagementController(SheetManagementService service) {
        this.service = service;
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@PathVariable UUID sheetId) {
        service.delete(sheetId, AuthenticatedUser.id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-requests")
    public ResponseEntity<ChangeRequestResponse> propose(@PathVariable UUID sheetId,
                                                         @Valid @RequestBody CreateChangeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.propose(sheetId, request, AuthenticatedUser.id()));
    }

    @GetMapping("/change-requests")
    public ResponseEntity<List<ChangeRequestResponse>> list(@PathVariable UUID sheetId) {
        return ResponseEntity.ok(service.list(sheetId, AuthenticatedUser.id()));
    }

    @PostMapping("/change-requests/{requestId}/review")
    public ResponseEntity<ChangeRequestResponse> review(@PathVariable UUID sheetId, @PathVariable UUID requestId,
                                                        @Valid @RequestBody ReviewChangeRequest request) {
        return ResponseEntity.ok(service.review(sheetId, requestId, request, AuthenticatedUser.id()));
    }

    @DeleteMapping("/change-requests/{requestId}")
    public ResponseEntity<Void> cancel(@PathVariable UUID sheetId, @PathVariable UUID requestId) {
        service.cancel(sheetId, requestId, AuthenticatedUser.id());
        return ResponseEntity.noContent().build();
    }
}
