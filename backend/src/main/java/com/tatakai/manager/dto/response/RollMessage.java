package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.RollChannel;
import com.tatakai.manager.entity.RollMode;
import com.tatakai.manager.entity.RollPart;
import com.tatakai.manager.entity.RollSourceType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mensagem de rolagem (resposta HTTP, tópico "rolls" e fila /user/queue/gm-rolls).
 * `expression`/`modifier` mantêm compatibilidade com a antiga resposta da rolagem livre.
 */
public record RollMessage(UUID id, UUID campaignId, UUID sheetId, UUID actorId, String actorName,
        String characterName, RollChannel channel, RollMode mode, String purpose, RollSourceType sourceType,
        UUID sourceId, String sourceName, String formula, String expression, int modifier,
        List<RollPart> breakdown, List<Integer> diceResults,
        int advantage, int total, Integer naturalD20, boolean critical, boolean fumble, Integer damageTotal,
        boolean edited, String editReason, boolean revealed, Instant createdAt,
        UUID customChannelId) {}
