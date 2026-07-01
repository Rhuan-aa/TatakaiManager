package com.tatakai.manager.dto.response;

import com.tatakai.manager.dto.request.NpcAttributesDto;
import com.tatakai.manager.dto.request.NpcDetailDto;
import com.tatakai.manager.entity.InteractionType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record NpcResponse(
        UUID id,
        String name,
        String description,
        NpcAttributesDto attributes,
        List<NpcDetailDto> knowledge,
        List<NpcDetailDto> traits,
        Set<InteractionType> interactionTypes,
        UUID ownerId,
        /** Visibilidade na campanha consultada (nulo fora de contexto de campanha). */
        Boolean visible
) {}
