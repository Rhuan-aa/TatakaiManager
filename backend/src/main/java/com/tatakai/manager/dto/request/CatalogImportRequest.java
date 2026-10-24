package com.tatakai.manager.dto.request;

import jakarta.validation.Valid;

import java.util.List;

public record CatalogImportRequest(
        @Valid List<TraitPayload> traits,
        @Valid List<ClanPayload> clans,
        @Valid List<MartialArtPayload> arts,
        @Valid List<InstinctPayload> instincts,
        @Valid List<FightMilestonePayload> milestones
) {}
