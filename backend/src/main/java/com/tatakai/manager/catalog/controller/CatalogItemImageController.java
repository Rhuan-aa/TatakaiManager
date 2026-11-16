package com.tatakai.manager.catalog.controller;

import com.tatakai.manager.catalog.dto.response.CatalogItemImageResponse;
import com.tatakai.manager.catalog.entity.CatalogItemImage;
import com.tatakai.manager.catalog.entity.CatalogItemKind;
import com.tatakai.manager.catalog.exception.CatalogItemNotFoundException;
import com.tatakai.manager.catalog.service.CatalogItemImageService;
import com.tatakai.manager.npc.exception.InvalidImageException;
import com.tatakai.manager.shared.security.AuthenticatedUser;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
public class CatalogItemImageController {

    private final CatalogItemImageService service;

    public CatalogItemImageController(CatalogItemImageService service) {
        this.service = service;
    }

    private static CatalogItemKind kind(String path) {
        return switch (path) {
            case "weapons" -> CatalogItemKind.WEAPON;
            case "equipment" -> CatalogItemKind.EQUIPMENT;
            case "consumables" -> CatalogItemKind.CONSUMABLE;
            default -> throw new CatalogItemNotFoundException("Tipo de item desconhecido");
        };
    }

    private ResponseEntity<Void> upload(String kind, String key, UUID cid, MultipartFile file) {
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new InvalidImageException("Não foi possível ler o arquivo enviado");
        }
        service.set(kind(kind), key, cid, AuthenticatedUser.id(), data, file.getContentType());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<byte[]> full(String kind, String key, UUID cid) {
        CatalogItemImage i = service.get(kind(kind), key, cid, AuthenticatedUser.id());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(i.getContentType())).body(i.getData());
    }

    private ResponseEntity<byte[]> thumb(String kind, String key, UUID cid) {
        CatalogItemImage i = service.get(kind(kind), key, cid, AuthenticatedUser.id());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(i.getThumbContentType())).body(i.getThumb());
    }

    // ---------- global ----------

    @PostMapping("/catalog/{kind}/{key}/image")
    public ResponseEntity<Void> uploadGlobal(@PathVariable String kind, @PathVariable String key,
                                             @RequestParam("file") MultipartFile file) {
        return upload(kind, key, null, file);
    }

    @DeleteMapping("/catalog/{kind}/{key}/image")
    public ResponseEntity<Void> deleteGlobal(@PathVariable String kind, @PathVariable String key) {
        service.delete(kind(kind), key, null, AuthenticatedUser.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/catalog/{kind}/{key}/image")
    public ResponseEntity<byte[]> getGlobal(@PathVariable String kind, @PathVariable String key) {
        return full(kind, key, null);
    }

    @GetMapping("/catalog/{kind}/{key}/image/thumb")
    public ResponseEntity<byte[]> getGlobalThumb(@PathVariable String kind, @PathVariable String key) {
        return thumb(kind, key, null);
    }

    @GetMapping("/catalog/item-images")
    public List<CatalogItemImageResponse> listGlobal() {
        return service.listGlobal();
    }

    // ---------- campanha ----------

    @PostMapping("/campaigns/{cid}/catalog/{kind}/{key}/image")
    public ResponseEntity<Void> uploadCampaign(@PathVariable UUID cid, @PathVariable String kind,
                                               @PathVariable String key, @RequestParam("file") MultipartFile file) {
        return upload(kind, key, cid, file);
    }

    @DeleteMapping("/campaigns/{cid}/catalog/{kind}/{key}/image")
    public ResponseEntity<Void> deleteCampaign(@PathVariable UUID cid, @PathVariable String kind,
                                               @PathVariable String key) {
        service.delete(kind(kind), key, cid, AuthenticatedUser.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/campaigns/{cid}/catalog/{kind}/{key}/image")
    public ResponseEntity<byte[]> getCampaign(@PathVariable UUID cid, @PathVariable String kind,
                                              @PathVariable String key) {
        return full(kind, key, cid);
    }

    @GetMapping("/campaigns/{cid}/catalog/{kind}/{key}/image/thumb")
    public ResponseEntity<byte[]> getCampaignThumb(@PathVariable UUID cid, @PathVariable String kind,
                                                   @PathVariable String key) {
        return thumb(kind, key, cid);
    }

    @GetMapping("/campaigns/{cid}/catalog/item-images")
    public List<CatalogItemImageResponse> listCampaign(@PathVariable UUID cid) {
        return service.listForCampaign(cid, AuthenticatedUser.id());
    }
}
