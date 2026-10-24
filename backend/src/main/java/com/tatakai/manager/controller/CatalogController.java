package com.tatakai.manager.controller;

import com.tatakai.manager.dto.request.*;
import com.tatakai.manager.dto.response.*;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/catalog/permissions")
    public ResponseEntity<CatalogPermissionsResponse> permissions() {
        return ResponseEntity.ok(catalog.permissions(AuthenticatedUser.id()));
    }

    @GetMapping("/catalog/traits")
    public ResponseEntity<List<TraitDefinitionResponse>> listTraits(@RequestParam(required = false) UUID campaignId) {
        return ResponseEntity.ok(catalog.listTraits(campaignId, AuthenticatedUser.id()));
    }

    @PostMapping("/catalog/traits")
    public ResponseEntity<TraitDefinitionResponse> createTrait(@Valid @RequestBody TraitPayload payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createTrait(null, payload, AuthenticatedUser.id()));
    }

    @PostMapping("/campaigns/{campaignId}/catalog/traits")
    public ResponseEntity<TraitDefinitionResponse> createCampaignTrait(@PathVariable UUID campaignId,
                                                                       @Valid @RequestBody TraitPayload payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createTrait(campaignId, payload, AuthenticatedUser.id()));
    }

    @PutMapping("/catalog/traits/{id}")
    public ResponseEntity<TraitDefinitionResponse> updateTrait(@PathVariable UUID id,
                                                               @Valid @RequestBody TraitPayload payload) {
        return ResponseEntity.ok(catalog.updateTrait(id, payload, AuthenticatedUser.id()));
    }

    @DeleteMapping("/catalog/traits/{id}")
    public ResponseEntity<Void> deleteTrait(@PathVariable UUID id) {
        catalog.deleteTrait(id, AuthenticatedUser.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/catalog/clans")
    public ResponseEntity<List<ClanResponse>> listClans(@RequestParam(required = false) UUID campaignId) {
        return ResponseEntity.ok(catalog.listClans(campaignId, AuthenticatedUser.id()));
    }

    @PostMapping("/catalog/clans")
    public ResponseEntity<ClanResponse> createClan(@Valid @RequestBody ClanPayload payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createClan(null, payload, AuthenticatedUser.id()));
    }

    @PostMapping("/campaigns/{campaignId}/catalog/clans")
    public ResponseEntity<ClanResponse> createCampaignClan(@PathVariable UUID campaignId,
                                                           @Valid @RequestBody ClanPayload payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createClan(campaignId, payload, AuthenticatedUser.id()));
    }

    @PutMapping("/catalog/clans/{id}")
    public ResponseEntity<ClanResponse> updateClan(@PathVariable UUID id, @Valid @RequestBody ClanPayload payload) {
        return ResponseEntity.ok(catalog.updateClan(id, payload, AuthenticatedUser.id()));
    }

    @DeleteMapping("/catalog/clans/{id}")
    public ResponseEntity<Void> deleteClan(@PathVariable UUID id) {
        catalog.deleteClan(id, AuthenticatedUser.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/catalog/arts")
    public ResponseEntity<List<MartialArtSummaryResponse>> listArts(@RequestParam(required = false) UUID campaignId) {
        return ResponseEntity.ok(catalog.listArts(campaignId, AuthenticatedUser.id()));
    }

    @GetMapping("/catalog/arts/{id}")
    public ResponseEntity<MartialArtResponse> getArt(@PathVariable UUID id) {
        return ResponseEntity.ok(catalog.getArt(id, AuthenticatedUser.id()));
    }

    @PostMapping("/catalog/arts")
    public ResponseEntity<MartialArtResponse> createGlobalArt(@Valid @RequestBody MartialArtPayload payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createArt(null, payload, AuthenticatedUser.id()));
    }

    @PostMapping("/campaigns/{campaignId}/catalog/arts")
    public ResponseEntity<MartialArtResponse> createCampaignArt(@PathVariable UUID campaignId,
                                                                @Valid @RequestBody MartialArtPayload payload) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(catalog.createArt(campaignId, payload, AuthenticatedUser.id()));
    }

    @PutMapping("/catalog/arts/{id}")
    public ResponseEntity<MartialArtResponse> updateArt(@PathVariable UUID id,
                                                        @Valid @RequestBody MartialArtPayload payload) {
        return ResponseEntity.ok(catalog.updateArt(id, payload, AuthenticatedUser.id()));
    }

    @PostMapping("/catalog/arts/{id}/submit")
    public ResponseEntity<MartialArtResponse> submitArt(@PathVariable UUID id) {
        return ResponseEntity.ok(catalog.submitArt(id, AuthenticatedUser.id()));
    }

    @PostMapping("/catalog/arts/{id}/review")
    public ResponseEntity<MartialArtResponse> reviewArt(@PathVariable UUID id,
                                                        @Valid @RequestBody ReviewArtRequest request) {
        return ResponseEntity.ok(catalog.reviewArt(id, request, AuthenticatedUser.id()));
    }

    @GetMapping("/catalog/modules")
    public ResponseEntity<List<AssemblyModule>> modules() {
        return ResponseEntity.ok(catalog.modules());
    }

    @PostMapping("/catalog/assembly/check")
    public ResponseEntity<AssemblyCheck> checkAssembly(@Valid @RequestBody AssemblyCheckRequest request) {
        return ResponseEntity.ok(catalog.checkAssembly(request));
    }

    @DeleteMapping("/catalog/arts/{id}")
    public ResponseEntity<Void> deleteArt(@PathVariable UUID id) {
        catalog.deleteArt(id, AuthenticatedUser.id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/catalog/import")
    public ResponseEntity<CatalogImportResponse> importGlobal(@Valid @RequestBody CatalogImportRequest request) {
        return ResponseEntity.ok(catalog.importCatalog(null, request, AuthenticatedUser.id()));
    }

    @PostMapping("/campaigns/{campaignId}/catalog/import")
    public ResponseEntity<CatalogImportResponse> importCampaign(@PathVariable UUID campaignId,
                                                                @Valid @RequestBody CatalogImportRequest request) {
        return ResponseEntity.ok(catalog.importCatalog(campaignId, request, AuthenticatedUser.id()));
    }
}
