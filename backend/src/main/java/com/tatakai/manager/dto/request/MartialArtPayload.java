package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.ArtBase;
import com.tatakai.manager.entity.ArtCategory;
import com.tatakai.manager.entity.ArtPassive;
import com.tatakai.manager.entity.ArtRule;
import com.tatakai.manager.entity.ArtTechnique;
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
        List<ArtTechnique> techniques
) {}
