package com.tatakai.manager.timeskip.dto.response;

import com.tatakai.manager.progression.entity.TrainingVenue;
import com.tatakai.manager.timeskip.entity.TimeSkipMode;
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
        TrainingVenue venue,
        TimeSkipMode mode,
        short slotsPerDay,
        /** Número do slot extra: 5 até 4 slots por dia, senão slotsPerDay + 1. */
        short extraSlot
) {
    public TimeSkipResponse(UUID id, UUID campaignId, String name, short totalDays, short currentDay,
                            TimeSkipStatus status, Instant createdAt, Instant closedAt, TrainingVenue venue,
                            TimeSkipMode mode) {
        this(id, campaignId, name, totalDays, currentDay, status, createdAt, closedAt, venue, mode, (short) 4, (short) 5);
    }

    public TimeSkipResponse(UUID id, UUID campaignId, String name, short totalDays, short currentDay,
                            TimeSkipStatus status, Instant createdAt, Instant closedAt, TrainingVenue venue) {
        this(id, campaignId, name, totalDays, currentDay, status, createdAt, closedAt, venue, TimeSkipMode.TIMESKIP, (short) 4, (short) 5);
    }
}
