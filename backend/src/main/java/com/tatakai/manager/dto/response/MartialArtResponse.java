package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.ArtBase;
import com.tatakai.manager.entity.ArtCategory;
import com.tatakai.manager.entity.ArtPassive;
import com.tatakai.manager.entity.ArtRule;
import com.tatakai.manager.entity.ArtTechnique;

import java.time.Instant;
import java.util.List;
import com.tatakai.manager.entity.ArtStatus;

import java.util.UUID;

public record MartialArtResponse(
        UUID id, UUID campaignId, String name, ArtCategory category, String rank, String martialType,
        List<String> attackSpecializations, List<String> reactionSpecializations, String requirement,
        String description, List<ArtRule> sections, List<ArtPassive> passives, List<ArtBase> bases,
        List<ArtTechnique> techniques, int version, Instant updatedAt,
        ArtStatus status, UUID authorId, String reviewNote, List<AssemblyCheck> techniqueChecks,
        List<AssemblyCheck> baseChecks
) {}
