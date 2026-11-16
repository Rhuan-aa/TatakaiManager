package com.tatakai.manager.sheet.controller;

import com.tatakai.manager.npc.exception.InvalidImageException;
import com.tatakai.manager.shared.security.AuthenticatedUser;
import com.tatakai.manager.sheet.entity.SheetImage;
import com.tatakai.manager.sheet.entity.SheetImageKind;
import com.tatakai.manager.sheet.service.SheetImageService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/character-sheets/{sheetId}/images/{kind}")
public class SheetImageController {

    private final SheetImageService imageService;

    public SheetImageController(SheetImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping
    public ResponseEntity<Void> upload(@PathVariable UUID sheetId, @PathVariable SheetImageKind kind,
                                       @RequestParam("file") MultipartFile file) {
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new InvalidImageException("Não foi possível ler o arquivo enviado");
        }
        imageService.set(sheetId, kind, AuthenticatedUser.id(), data, file.getContentType());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@PathVariable UUID sheetId, @PathVariable SheetImageKind kind) {
        imageService.delete(sheetId, kind, AuthenticatedUser.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<byte[]> get(@PathVariable UUID sheetId, @PathVariable SheetImageKind kind) {
        SheetImage image = imageService.get(sheetId, kind, AuthenticatedUser.id());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.getContentType()))
                .body(image.getData());
    }
}
