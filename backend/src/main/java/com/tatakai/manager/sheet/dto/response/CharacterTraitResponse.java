package com.tatakai.manager.sheet.dto.response;

import com.tatakai.manager.catalog.entity.TraitAcquisition;
import com.tatakai.manager.catalog.entity.TraitCategory;
import com.tatakai.manager.catalog.entity.TraitRarity;

import java.util.UUID;

public record CharacterTraitResponse(
        UUID id, UUID traitId, int traitVersion, String name, TraitRarity rarity, TraitCategory category,
        String description, TraitAcquisition acquiredIn, boolean gainsApplied
) {}
