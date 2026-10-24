package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.ArtCategory;

import java.util.List;
import com.tatakai.manager.entity.ArtStatus;

import java.util.UUID;

public record MartialArtSummaryResponse(
        UUID id, UUID campaignId, String name, ArtCategory category, String martialType,
        List<String> attackSpecializations, List<String> reactionSpecializations,
        int bases, int techniques, int version, ArtStatus status, UUID authorId
) {}
