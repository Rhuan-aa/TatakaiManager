package com.tatakai.manager.npc.dto.response;

import com.tatakai.manager.npc.dto.request.NpcAttributesDto;
import com.tatakai.manager.npc.dto.request.NpcDetailDto;
import com.tatakai.manager.npc.dto.request.NpcInteractionDto;

import java.util.List;
import java.util.UUID;

public record NpcResponse(
        UUID id,
        String name,
        String description,
        NpcAttributesDto attributes,
        List<NpcDetailDto> knowledge,
        List<NpcDetailDto> traits,
        List<NpcDetailDto> specs,
        List<NpcInteractionDto> interactions,
        UUID ownerId,
        /** Indica se o NPC tem imagem (retrato) cadastrada. */
        boolean hasImage,
        /** Visibilidade na campanha consultada (nulo fora de contexto de campanha). */
        Boolean visible,
        /** Id da ficha digital do NPC (null se o NPC não tem ficha). */
        UUID sheetId
) {}
