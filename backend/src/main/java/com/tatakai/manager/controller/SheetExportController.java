package com.tatakai.manager.controller;

import com.tatakai.manager.dto.response.SheetExportFile;
import com.tatakai.manager.dto.response.SheetImportResponse;
import com.tatakai.manager.exception.InvalidCharacterSheetException;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.SheetExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
public class SheetExportController {

    private final SheetExportService exportService;

    public SheetExportController(SheetExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/character-sheets/{sheetId}/export")
    public ResponseEntity<SheetExportFile> export(@PathVariable UUID sheetId) {
        SheetExportFile file = exportService.export(sheetId, AuthenticatedUser.id());
        String base = file.sheet().name() == null ? "ficha" : file.sheet().name().replaceAll("[^\\p{L}\\p{N}_-]+", "_");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(base + ".tatakai.json", StandardCharsets.UTF_8).build().toString())
                .body(file);
    }

    @PostMapping("/campaigns/{campaignId}/character-sheets/import")
    public ResponseEntity<SheetImportResponse> importSheet(@PathVariable UUID campaignId,
                                                           @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(exportService.importSheet(
                    campaignId, file.getBytes(), file.getOriginalFilename(), AuthenticatedUser.id()));
        } catch (IOException e) {
            throw new InvalidCharacterSheetException("Não foi possível ler o arquivo");
        }
    }
}
