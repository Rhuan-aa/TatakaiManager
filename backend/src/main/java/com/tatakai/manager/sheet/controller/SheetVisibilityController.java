package com.tatakai.manager.sheet.controller;

import com.tatakai.manager.shared.security.AuthenticatedUser;
import com.tatakai.manager.sheet.dto.request.SetSectionVisibilityRequest;
import com.tatakai.manager.sheet.dto.response.SectionVisibilityResponse;
import com.tatakai.manager.sheet.service.SheetVisibilityService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/campaigns/{campaignId}/members/{userId}/sheet/visibility")
public class SheetVisibilityController {

    private final SheetVisibilityService sheetVisibilityService;

    public SheetVisibilityController(SheetVisibilityService sheetVisibilityService) {
        this.sheetVisibilityService = sheetVisibilityService;
    }

    @GetMapping
    public ResponseEntity<List<SectionVisibilityResponse>> get(@PathVariable UUID campaignId,
                                                              @PathVariable UUID userId) {
        return ResponseEntity.ok(
                sheetVisibilityService.getVisibility(campaignId, userId, AuthenticatedUser.id()));
    }

    @PatchMapping
    public ResponseEntity<SectionVisibilityResponse> set(@PathVariable UUID campaignId,
                                                        @PathVariable UUID userId,
                                                        @Valid @RequestBody SetSectionVisibilityRequest request) {
        return ResponseEntity.ok(sheetVisibilityService.setVisibility(
                campaignId, userId, request.section(), request.hidden(), AuthenticatedUser.id()));
    }
}
