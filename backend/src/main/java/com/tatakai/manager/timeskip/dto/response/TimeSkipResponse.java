package com.tatakai.manager.timeskip.dto.response;

import com.tatakai.manager.progression.entity.TrainingVenue;
import com.tatakai.manager.timeskip.entity.TimeSkipStatus;

import java.time.Instant;
import java.util.UUID;

public record TimeSkipResponse(
        UUID id,
        UUID campaignId,
        String name,
        short totalDays,
        short currentDay,
        TimeSkipStatus status,
        Instant createdAt,
        Instant closedAt,
        com.tatakai.manager.progression.entity.TrainingVenue venue
) {}
