package com.tatakai.manager.timeskip.dto.response;

import com.tatakai.manager.progression.entity.RestQuality;
import com.tatakai.manager.timeskip.entity.SoloActivityType;

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
        RestQuality restQuality,
        UUID characterArtId
) {}
