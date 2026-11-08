package com.tatakai.manager.combat.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** `team` opcional (1..9); sem ele, jogador entra no time 1 e NPC no 2. */
public record AddParticipantRequest(@NotNull UUID sheetId, @Min(1) @Max(9) Integer team) {
    public AddParticipantRequest(UUID sheetId) {
        this(sheetId, null);
    }
}
