package com.tatakai.manager.combat.dto.response;

import com.tatakai.manager.combat.entity.EncounterStatus;

import java.time.Instant;
import java.util.UUID;

public record EncounterSummaryResponse(UUID id, String name, EncounterStatus status, int round, Instant createdAt) {
}
