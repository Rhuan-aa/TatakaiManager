package com.tatakai.manager.controller;

import com.tatakai.manager.dto.request.CreateNpcRequest;
import com.tatakai.manager.dto.request.SetVisibilityRequest;
import com.tatakai.manager.dto.request.UpdateNpcRequest;
import com.tatakai.manager.dto.response.CampaignNpcResponse;
import com.tatakai.manager.dto.response.NpcResponse;
import com.tatakai.manager.dto.response.NpcSummaryResponse;
import com.tatakai.manager.entity.NpcImage;
import com.tatakai.manager.exception.InvalidImageException;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.NpcService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
public class NpcController {

    private final NpcService npcService;

    public NpcController(NpcService npcService) {
        this.npcService = npcService;
    }

    // ---- Acervo do Mestre (US-06, US-07) ----

    @GetMapping("/npcs")
    public ResponseEntity<List<NpcSummaryResponse>> listOwned() {
        return ResponseEntity.ok(npcService.listOwned(AuthenticatedUser.id()));
    }

    @PostMapping("/npcs")
    public ResponseEntity<NpcResponse> create(@Valid @RequestBody CreateNpcRequest request) {
        NpcResponse res = npcService.create(AuthenticatedUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @PutMapping("/npcs/{npcId}")
    public ResponseEntity<NpcResponse> update(@PathVariable UUID npcId,
                                              @Valid @RequestBody UpdateNpcRequest request) {
        return ResponseEntity.ok(npcService.update(npcId, AuthenticatedUser.id(), request));
    }

    // ---- Imagem (retrato) do NPC ----

    @PostMapping("/npcs/{npcId}/image")
    public ResponseEntity<Void> uploadImage(@PathVariable UUID npcId,
                                            @RequestParam("file") MultipartFile file) {
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new InvalidImageException("Não foi possível ler o arquivo enviado");
        }
        npcService.setImage(npcId, AuthenticatedUser.id(), data, file.getContentType());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/npcs/{npcId}/image")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID npcId) {
        npcService.deleteImage(npcId, AuthenticatedUser.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/campaigns/{campaignId}/npcs/{npcId}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable UUID campaignId,
                                           @PathVariable UUID npcId) {
        NpcImage image = npcService.getImage(campaignId, npcId, AuthenticatedUser.id());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .body(image.getData());
    }

    // ---- Associação e visibilidade por campanha (US-08, US-20) ----

    @PostMapping("/campaigns/{campaignId}/npcs/{npcId}")
    public ResponseEntity<CampaignNpcResponse> associate(@PathVariable UUID campaignId,
                                                         @PathVariable UUID npcId) {
        CampaignNpcResponse res = npcService.associate(npcId, campaignId, AuthenticatedUser.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @PatchMapping("/campaigns/{campaignId}/npcs/{npcId}/visibility")
    public ResponseEntity<CampaignNpcResponse> setVisibility(@PathVariable UUID campaignId,
                                                            @PathVariable UUID npcId,
                                                            @Valid @RequestBody SetVisibilityRequest request) {
        CampaignNpcResponse res = npcService.setVisibility(
                campaignId, npcId, AuthenticatedUser.id(), request.visible());
        return ResponseEntity.ok(res);
    }

    @GetMapping("/campaigns/{campaignId}/npcs")
    public ResponseEntity<List<NpcSummaryResponse>> listForCampaign(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(npcService.listForCampaign(campaignId, AuthenticatedUser.id()));
    }

    @GetMapping("/campaigns/{campaignId}/npcs/{npcId}")
    public ResponseEntity<NpcResponse> getForCampaign(@PathVariable UUID campaignId,
                                                      @PathVariable UUID npcId) {
        return ResponseEntity.ok(npcService.getForCampaign(campaignId, npcId, AuthenticatedUser.id()));
    }
}
