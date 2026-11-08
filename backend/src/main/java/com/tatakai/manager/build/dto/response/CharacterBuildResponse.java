package com.tatakai.manager.build.dto.response;

import com.tatakai.manager.build.entity.BuildSettings;
import com.tatakai.manager.build.entity.BuildStatus;
import com.tatakai.manager.build.entity.BuildStep;
import com.tatakai.manager.build.entity.TraitSlot;
import com.tatakai.manager.build.service.rules.BuildRules;
import com.tatakai.manager.catalog.dto.response.ClanResponse;
import com.tatakai.manager.sheet.entity.Attribute;
import com.tatakai.manager.sheet.entity.BodyType;
import com.tatakai.manager.sheet.entity.Dominance;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CharacterBuildResponse(
        UUID sheetId, String name, BuildStep step, BuildSettings settings, BodyType bodyType, Dominance dominance,
        Double heightM, Double weightKg, Integer traitCountRoll, List<TraitSlot> traitSlots, boolean traitsConfirmed,
        Map<com.tatakai.manager.catalog.entity.TraitRarity, List<UUID>> traitTables,
        Integer bloodTestRoll, int clanSlots, List<ClanResponse> clans, boolean clansConfirmed,
        List<Integer> attributeDice, Map<Attribute, Integer> assignment, boolean attributesConfirmed,
        Map<Attribute, BuildRules.AttributeLayer> layers,   // null até haver dados
        int freePointsAvailable, Map<Attribute, Integer> freePoints, boolean freePointsConfirmed,
        BuildRules.ResourceMaxes resources,                 // null até atributos
        BuildStatus buildStatus,
        List<Integer> clanRolls,                             // d20 de cada clã sorteado (clã aleatório)
        Map<Attribute, BuildRules.AttributeLayer> bonuses    // bônus de clãs/traços por atributo (base 0): clanFlat/traitFlat/percent
) {}
