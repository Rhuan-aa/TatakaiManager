package com.tatakai.manager.sheet.dto.response;

import com.tatakai.manager.sheet.entity.ActivityCategory;
import com.tatakai.manager.sheet.entity.ActorRole;

import java.time.Instant;
import java.util.UUID;

public record SheetActivityLogResponse(UUID id, UUID sheetId, UUID actorId, String actorName,
        ActorRole actorRole, ActivityCategory category, String action, String field, String beforeValue,
        String afterValue, UUID rollId, String reason, Instant createdAt) {}
