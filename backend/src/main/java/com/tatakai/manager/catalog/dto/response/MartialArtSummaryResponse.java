package com.tatakai.manager.catalog.dto.response;

import com.tatakai.manager.catalog.entity.ArtCategory;
import com.tatakai.manager.catalog.entity.ArtStatus;

import java.util.List;

import java.util.UUID;

public record MartialArtSummaryResponse(
        UUID id, UUID campaignId, String name, ArtCategory category, String martialType,
        List<String> attackSpecializations, List<String> reactionSpecializations,
        int bases, int techniques, int version, ArtStatus status, UUID authorId,
        boolean hidden
) {}
