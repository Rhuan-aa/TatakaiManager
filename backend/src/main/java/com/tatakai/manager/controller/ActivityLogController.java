package com.tatakai.manager.controller;

import com.tatakai.manager.dto.response.SheetActivityLogResponse;
import com.tatakai.manager.entity.ActivityCategory;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.SheetActivityLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
public class ActivityLogController {

    private final SheetActivityLogService activityLogService;

    public ActivityLogController(SheetActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @GetMapping("/character-sheets/{sheetId}/activity-log")
    public ResponseEntity<List<SheetActivityLogResponse>> listForSheet(@PathVariable UUID sheetId) {
        return ResponseEntity.ok(activityLogService.listForSheet(sheetId, AuthenticatedUser.id()));
    }

    @GetMapping("/campaigns/{campaignId}/activity-log")
    public ResponseEntity<List<SheetActivityLogResponse>> listForCampaign(
            @PathVariable UUID campaignId,
            @RequestParam(required = false) UUID sheetId,
            @RequestParam(required = false) ActivityCategory category,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(activityLogService.listForCampaign(
                campaignId, sheetId, category, from, to, AuthenticatedUser.id()));
    }
}
