package com.tatakai.manager.catalog.dto.response;

import com.tatakai.manager.build.entity.BuildGains;
import com.tatakai.manager.catalog.entity.TraitActivation;
import com.tatakai.manager.catalog.entity.TraitCategory;
import com.tatakai.manager.catalog.entity.TraitEffect;
import com.tatakai.manager.catalog.entity.TraitRarity;
import com.tatakai.manager.catalog.entity.TraitVariation;

import java.util.List;
import java.util.UUID;

public record TraitDefinitionResponse(
        UUID id, UUID campaignId, String name, TraitRarity rarity, TraitCategory category, Integer rollNumber,
        String description, List<TraitEffect> effects, BuildGains buildGains, boolean developable,
        boolean upgradable, int version, TraitActivation activation, String requirements,
        String developmentRequirements, List<TraitVariation> variations
) {}
