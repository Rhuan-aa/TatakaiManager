package com.tatakai.manager.catalog.dto.request;

import com.tatakai.manager.catalog.entity.ArtBase;
import com.tatakai.manager.catalog.entity.ArtCategory;
import com.tatakai.manager.catalog.entity.ArtPassive;
import com.tatakai.manager.catalog.entity.ArtRule;
import com.tatakai.manager.catalog.entity.ArtTechnique;
import com.tatakai.manager.catalog.entity.BorrowedTechnique;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MartialArtPayload(
        @NotBlank @Size(max = 100) String name,
        @NotNull ArtCategory category,
        @Size(max = 100) String rank,
        @Size(max = 200) String martialType,
        List<String> attackSpecializations,
        List<String> reactionSpecializations,
        @Size(max = 10000) String requirement,
        @Size(max = 10000) String description,
        List<ArtRule> sections,
        List<ArtPassive> passives,
        List<ArtBase> bases,
        List<ArtTechnique> techniques,
        List<BorrowedTechnique> borrowed,
        Boolean hidden
) {
    public MartialArtPayload(String name, ArtCategory category, String rank, String martialType,
                             List<String> attackSpecializations, List<String> reactionSpecializations,
                             String requirement, String description, List<ArtRule> sections, List<ArtPassive> passives,
                             List<ArtBase> bases, List<ArtTechnique> techniques, List<BorrowedTechnique> borrowed) {
        this(name, category, rank, martialType, attackSpecializations, reactionSpecializations, requirement,
                description, sections, passives, bases, techniques, borrowed, null);
    }
}
