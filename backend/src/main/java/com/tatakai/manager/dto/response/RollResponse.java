package com.tatakai.manager.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** naturalD20 só quando expression é exatamente 1d20(+/-n); critical = 20; fumble = 1. */
public record RollResponse(UUID id, UUID sheetId, UUID actorId, String actorName, String expression,
        List<Integer> diceResults, int modifier, int total, Integer naturalD20, boolean critical, boolean fumble,
        String purpose, Instant createdAt) {}
