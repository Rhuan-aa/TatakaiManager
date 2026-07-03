package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.SoloActivityType;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID npcId,
        String npcName,
        UUID userId,
        String userName,
        short dayNumber,
        short slotNumber,
        String interactionName,
        short idlePointCost,
        SoloActivityType soloActivityType,
        String description,
        Instant createdAt
) {}
