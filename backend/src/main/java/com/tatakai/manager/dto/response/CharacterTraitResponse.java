package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.TraitAcquisition;
import com.tatakai.manager.entity.TraitCategory;
import com.tatakai.manager.entity.TraitRarity;

import java.util.UUID;

public record CharacterTraitResponse(
        UUID id, UUID traitId, int traitVersion, String name, TraitRarity rarity, TraitCategory category,
        String description, TraitAcquisition acquiredIn, boolean gainsApplied
) {}
