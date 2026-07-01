package com.tatakai.manager.controller;

import com.tatakai.manager.dto.request.SetSectionVisibilityRequest;
import com.tatakai.manager.dto.response.SectionVisibilityResponse;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.SheetVisibilityService;
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
