package com.tatakai.manager.sheet.dto.response;

import com.tatakai.manager.build.entity.BuildStatus;
import com.tatakai.manager.progression.dto.response.SheetProgress;
import com.tatakai.manager.sheet.entity.Attribute;
import com.tatakai.manager.sheet.entity.BodyPart;
import com.tatakai.manager.sheet.entity.BodyType;
import com.tatakai.manager.sheet.entity.Dominance;

import com.tatakai.manager.npc.dto.request.NpcInteractionDto;
import com.tatakai.manager.sheet.entity.SheetSection;

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
        List<CharacterClanResponse> clans,        // null se TRAITS oculta
        boolean combatReady,                      // false = ficha leve de NPC (sem atributos/recursos)
        boolean hasPortrait, boolean hasFullBody,
        List<BodyPart> missingParts,              // null se INJURIES ou BODY_MAP oculta
        List<NpcInteractionDto> interactions,     // só ficha de NPC vista na campanha; null se INTERACTIONS oculta
        List<SheetSection> visibleSections,       // só ficha de NPC vista na campanha; null nos demais casos
        boolean npcTracking,                      // ficha de NPC com Cansaço/PS ligados (§53.2-6)
        List<WoundMarkResponse> wounds            // ferimentos desenhados (§53.3); null como missingParts
) {
    public CharacterSheetResponse withNpcView(List<NpcInteractionDto> interactions, List<SheetSection> visibleSections) {
        return new CharacterSheetResponse(id, campaignId, ownerUserId, ownerName, name, buildStatus, bodyType, dominance,
                heightM, weightKg, attributes, derived, resources, fatigue, progress, money, injuries, inventory,
                version, npcId, traits, clans, combatReady, hasPortrait, hasFullBody, missingParts,
                interactions, visibleSections, npcTracking, wounds);
    }
}
