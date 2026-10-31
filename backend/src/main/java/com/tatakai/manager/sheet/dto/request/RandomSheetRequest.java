package com.tatakai.manager.sheet.dto.request;

import com.tatakai.manager.catalog.entity.Graduation;
import com.tatakai.manager.catalog.entity.TraitCategory;
import com.tatakai.manager.catalog.entity.TraitRarity;
import com.tatakai.manager.sheet.service.rules.RandomSheetRules.Profile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Ficha aleatória configurável do Mestre (§51.9). Campos nulos = padrão do perfil. */
public record RandomSheetRequest(
        @NotNull Profile profile,
        @Size(max = 100) @Pattern(regexp = "^[^<>]*$") String name,
        boolean visible,
        /** NONE | RANDOM | SPECIFIC (usa artId) */
        @NotNull ArtMode artMode,
        UUID artId,
        /** null = padrão do perfil */
        Graduation graduation,
        @Min(0) @Max(8) Integer traitCount,
        TraitRarity traitRarity,
        TraitCategory traitCategory,
        Boolean developableOnly
) {
    public enum ArtMode { NONE, RANDOM, SPECIFIC }
}
