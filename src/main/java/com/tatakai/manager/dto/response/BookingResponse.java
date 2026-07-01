package com.tatakai.manager.dto.response;

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
        Instant createdAt
) {}
