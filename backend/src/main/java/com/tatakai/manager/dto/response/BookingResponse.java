package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.RestQuality;
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
        UUID activityId,
        String activityName,
        String description,
        Instant createdAt,
        String trainingTarget,
        UUID techniqueId,
        RestQuality restQuality
) {}
