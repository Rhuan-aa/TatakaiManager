package com.tatakai.manager.controller;

import com.tatakai.manager.dto.request.CreateLogRequest;
import com.tatakai.manager.dto.response.LogResponse;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.LogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/campaigns/{campaignId}/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @PostMapping
    public ResponseEntity<LogResponse> create(@PathVariable UUID campaignId,
                                              @Valid @RequestBody CreateLogRequest request) {
        LogResponse res = logService.create(campaignId, AuthenticatedUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping
    public ResponseEntity<List<LogResponse>> list(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(logService.listForCampaign(campaignId, AuthenticatedUser.id()));
    }
}
