package com.tatakai.manager.controller;

import com.tatakai.manager.dto.request.CreateTimeSkipRequest;
import com.tatakai.manager.dto.request.SetCurrentDayRequest;
import com.tatakai.manager.dto.response.TimeSkipResponse;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.TimeSkipService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class TimeSkipController {

    private final TimeSkipService timeSkipService;

    public TimeSkipController(TimeSkipService timeSkipService) {
        this.timeSkipService = timeSkipService;
    }

    @PostMapping("/campaigns/{campaignId}/timeskips")
    public ResponseEntity<TimeSkipResponse> create(@PathVariable UUID campaignId,
                                                   @Valid @RequestBody CreateTimeSkipRequest request) {
        TimeSkipResponse res = timeSkipService.create(campaignId, AuthenticatedUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping("/campaigns/{campaignId}/timeskips")
    public ResponseEntity<List<TimeSkipResponse>> list(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(timeSkipService.listForCampaign(campaignId, AuthenticatedUser.id()));
    }

    @PatchMapping("/timeskips/{timeSkipId}/close")
    public ResponseEntity<TimeSkipResponse> close(@PathVariable UUID timeSkipId) {
        return ResponseEntity.ok(timeSkipService.close(timeSkipId, AuthenticatedUser.id()));
    }

    @DeleteMapping("/timeskips/{timeSkipId}")
    public ResponseEntity<Void> delete(@PathVariable UUID timeSkipId) {
        timeSkipService.delete(timeSkipId, AuthenticatedUser.id());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/timeskips/{timeSkipId}/current-day")
    public ResponseEntity<TimeSkipResponse> setCurrentDay(@PathVariable UUID timeSkipId,
                                                          @Valid @RequestBody SetCurrentDayRequest request) {
        return ResponseEntity.ok(
                timeSkipService.setCurrentDay(timeSkipId, AuthenticatedUser.id(), request.currentDay()));
    }
}
