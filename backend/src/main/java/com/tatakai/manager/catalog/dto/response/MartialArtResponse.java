package com.tatakai.manager.catalog.dto.response;

import com.tatakai.manager.catalog.entity.ArtBase;
import com.tatakai.manager.catalog.entity.ArtCategory;
import com.tatakai.manager.catalog.entity.ArtPassive;
import com.tatakai.manager.catalog.entity.ArtRule;
import com.tatakai.manager.catalog.entity.ArtStatus;
import com.tatakai.manager.catalog.entity.ArtTechnique;
import com.tatakai.manager.catalog.entity.BorrowedTechnique;

import java.time.Instant;
import java.util.List;

import java.util.UUID;

public record MartialArtResponse(
        UUID id, UUID campaignId, String name, ArtCategory category, String rank, String martialType,
        List<String> attackSpecializations, List<String> reactionSpecializations, String requirement,
        String description, List<ArtRule> sections, List<ArtPassive> passives, List<ArtBase> bases,
        List<ArtTechnique> techniques, int version, Instant updatedAt,
        ArtStatus status, UUID authorId, String reviewNote, List<AssemblyCheck> techniqueChecks,
        List<AssemblyCheck> baseChecks,
        List<BorrowedTechnique> borrowed,
        boolean hidden
) {}
