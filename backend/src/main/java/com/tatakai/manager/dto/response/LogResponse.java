package com.tatakai.manager.dto.response;

import java.time.Instant;
import java.util.UUID;

public record LogResponse(
        UUID id,
        UUID authorId,
        String authorName,
        String narrative,
        Instant createdAt,
        Instant updatedAt,
        // Dados do agendamento relacionado (nulos em log livre do Mestre)
        UUID bookingId,
        UUID npcId,
        String npcName,
        Short dayNumber,
        Short slotNumber,
        String interactionName,
        // Custo em pontos de ócio do agendamento (nulo em log livre)
        Short idlePointCost
) {}
