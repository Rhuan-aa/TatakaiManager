package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.BuildGains;
import com.tatakai.manager.entity.TraitActivation;
import com.tatakai.manager.entity.TraitCategory;
import com.tatakai.manager.entity.TraitEffect;
import com.tatakai.manager.entity.TraitRarity;
import com.tatakai.manager.entity.TraitVariation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TraitPayload(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String name,
        @NotNull TraitRarity rarity,
        @NotNull TraitCategory category,
        @Min(1) @Max(20) Integer rollNumber,
        @Size(max = 4000) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String description,
        List<TraitEffect> effects,
        BuildGains buildGains,
        Boolean developable,
        Boolean upgradable,
        TraitActivation activation,
        @Size(max = 2000) String requirements,
        @Size(max = 2000) String developmentRequirements,
        List<TraitVariation> variations
) {}
