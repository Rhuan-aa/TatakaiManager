package com.tatakai.manager.approval.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Pendência para o Mestre. Campos que não se aplicam ao {@code kind} vêm nulos. */
public record ApprovalItemResponse(
        String kind,
        UUID id,
        String title,
        String requesterName,
        Instant createdAt,
        UUID sheetId,
        UUID timeSkipId,
        UUID artId,
        UUID logId,
        UUID userId) {
}
