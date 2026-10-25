package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.EncounterStatus;

import java.time.Instant;
import java.util.UUID;

public record EncounterSummaryResponse(UUID id, String name, EncounterStatus status, int round, Instant createdAt) {
}
