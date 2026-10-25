package com.tatakai.manager.controller;

import com.tatakai.manager.dto.request.RollChannelRequest;
import com.tatakai.manager.dto.response.RollChannelResponse;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.RollChannelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/campaigns/{campaignId}/roll-channels")
public class RollChannelController {

    private final RollChannelService service;

    public RollChannelController(RollChannelService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<RollChannelResponse>> list(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(service.list(campaignId, AuthenticatedUser.id()));
    }

    @PostMapping
    public ResponseEntity<RollChannelResponse> create(@PathVariable UUID campaignId,
                                                      @Valid @RequestBody RollChannelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(campaignId, request, AuthenticatedUser.id()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RollChannelResponse> update(@PathVariable UUID campaignId, @PathVariable UUID id,
                                                      @Valid @RequestBody RollChannelRequest request) {
        return ResponseEntity.ok(service.update(campaignId, id, request, AuthenticatedUser.id()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID campaignId, @PathVariable UUID id) {
        service.delete(campaignId, id, AuthenticatedUser.id());
        return ResponseEntity.noContent().build();
    }
}
