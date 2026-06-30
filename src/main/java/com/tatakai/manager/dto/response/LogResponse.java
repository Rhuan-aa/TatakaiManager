package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.InteractionType;

import java.time.Instant;
import java.util.UUID;

public record LogResponse(
        UUID id,
        UUID authorId,
        String authorName,
        String narrative,
        Instant createdAt,
        // Dados do agendamento relacionado (nulos em log livre do Mestre)
        UUID bookingId,
        UUID npcId,
        String npcName,
        Short dayNumber,
        Short slotNumber,
        InteractionType interactionType
) {}
