package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.BodyType;
import com.tatakai.manager.entity.BuildStatus;
import com.tatakai.manager.entity.Dominance;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CharacterSheetResponse(
        UUID id, UUID campaignId, UUID ownerUserId, String ownerName, String name,
        BuildStatus buildStatus, BodyType bodyType, Dominance dominance, double heightM, double weightKg,
        Map<Attribute, Integer> attributes,       // null se ATTRIBUTES oculta
        SheetDerived derived,                     // null se ATTRIBUTES oculta
        SheetResources resources,                 // null se RESOURCES oculta
        SheetFatigue fatigue,                     // null se RESOURCES oculta
        SheetProgress progress,                   // null se RESOURCES oculta
        Integer money,                            // null se INVENTORY oculta
        List<InjuryResponse> injuries,            // null se INJURIES oculta
        List<InventoryItemResponse> inventory,    // null se INVENTORY oculta
        long version,
        UUID npcId,                               // null em ficha de jogador
        List<CharacterTraitResponse> traits,      // null se TRAITS oculta
        List<CharacterClanResponse> clans         // null se TRAITS oculta
) {}
