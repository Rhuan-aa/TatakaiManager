package com.tatakai.manager.controller;

import com.tatakai.manager.dto.request.TimeSkipActivityRequest;
import com.tatakai.manager.dto.response.TimeSkipActivityResponse;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.TimeSkipActivityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/campaigns/{campaignId}/timeskips/{timeSkipId}/activities")
public class TimeSkipActivityController {

    private final TimeSkipActivityService activityService;

    public TimeSkipActivityController(TimeSkipActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping
    public ResponseEntity<TimeSkipActivityResponse> create(@PathVariable UUID campaignId,
                                                            @PathVariable UUID timeSkipId,
                                                            @Valid @RequestBody TimeSkipActivityRequest request) {
        TimeSkipActivityResponse res = activityService.create(
                campaignId, timeSkipId, AuthenticatedUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping
    public ResponseEntity<List<TimeSkipActivityResponse>> list(@PathVariable UUID campaignId,
                                                                @PathVariable UUID timeSkipId) {
        return ResponseEntity.ok(
                activityService.listForTimeSkip(campaignId, timeSkipId, AuthenticatedUser.id()));
    }

    @PutMapping("/{activityId}")
    public ResponseEntity<TimeSkipActivityResponse> update(@PathVariable UUID campaignId,
                                                            @PathVariable UUID timeSkipId,
                                                            @PathVariable UUID activityId,
                                                            @Valid @RequestBody TimeSkipActivityRequest request) {
        TimeSkipActivityResponse res = activityService.update(
                campaignId, timeSkipId, activityId, AuthenticatedUser.id(), request);
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/{activityId}")
    public ResponseEntity<Void> delete(@PathVariable UUID campaignId,
                                       @PathVariable UUID timeSkipId,
                                       @PathVariable UUID activityId) {
        activityService.delete(campaignId, timeSkipId, activityId, AuthenticatedUser.id());
        return ResponseEntity.noContent().build();
    }
}
