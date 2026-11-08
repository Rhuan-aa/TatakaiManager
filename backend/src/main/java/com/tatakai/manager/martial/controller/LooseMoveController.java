package com.tatakai.manager.martial.controller;

import com.tatakai.manager.martial.dto.request.LooseMoveRequest;
import com.tatakai.manager.martial.dto.response.LooseMoveResponse;
import com.tatakai.manager.martial.service.LooseMoveService;
import com.tatakai.manager.shared.security.AuthenticatedUser;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Catálogo de técnicas e bases avulsas (Mestre cria/oculta; jogadores listam as visíveis). */
@RestController
@RequestMapping("/campaigns/{campaignId}/loose-moves")
public class LooseMoveController {

    private final LooseMoveService service;

    public LooseMoveController(LooseMoveService service) {
        this.service = service;
    }

    @GetMapping
    public List<LooseMoveResponse> list(@PathVariable UUID campaignId) {
        return service.list(campaignId, AuthenticatedUser.id());
    }

    @PostMapping
    public ResponseEntity<LooseMoveResponse> create(@PathVariable UUID campaignId, @Valid @RequestBody LooseMoveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(campaignId, request, AuthenticatedUser.id()));
    }

    @PatchMapping("/{id}/visibility")
    public LooseMoveResponse visibility(@PathVariable UUID campaignId, @PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        return service.setVisible(campaignId, id, Boolean.TRUE.equals(body.get("visible")), AuthenticatedUser.id());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID campaignId, @PathVariable UUID id) {
        service.delete(campaignId, id, AuthenticatedUser.id());
        return ResponseEntity.noContent().build();
    }
}
