package com.tatakai.manager.combat.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddParticipantRequest(@NotNull UUID sheetId) {
}
