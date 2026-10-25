package com.tatakai.manager.combat.dto.response;

import com.tatakai.manager.combat.entity.CombatEventType;

import java.time.Instant;
import java.util.UUID;

public record CombatEventResponse(UUID id, int round, int turnCounter, UUID participantId,
                                  CombatEventType type, String text, Instant createdAt) {
}
