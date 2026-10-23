package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.BodyType;
import com.tatakai.manager.entity.BuildSettings;
import com.tatakai.manager.entity.BuildStatus;
import com.tatakai.manager.entity.BuildStep;
import com.tatakai.manager.entity.Dominance;
import com.tatakai.manager.entity.TraitSlot;
import com.tatakai.manager.service.rules.BuildRules;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CharacterBuildResponse(
        UUID sheetId, String name, BuildStep step, BuildSettings settings, BodyType bodyType, Dominance dominance,
        Double heightM, Double weightKg, Integer traitCountRoll, List<TraitSlot> traitSlots, boolean traitsConfirmed,
        Integer bloodTestRoll, int clanSlots, List<ClanResponse> clans, boolean clansConfirmed,
        List<Integer> attributeDice, Map<Attribute, Integer> assignment, boolean attributesConfirmed,
        Map<Attribute, BuildRules.AttributeLayer> layers,   // null até haver dados
        int freePointsAvailable, Map<Attribute, Integer> freePoints, boolean freePointsConfirmed,
        BuildRules.ResourceMaxes resources,                 // null até atributos
        BuildStatus buildStatus
) {}
