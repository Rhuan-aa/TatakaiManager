package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.InteractionType;

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
        InteractionType interactionType,
        Instant createdAt
) {}
