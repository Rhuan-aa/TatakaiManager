package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.TimeSkipStatus;

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
        Instant closedAt
) {}
