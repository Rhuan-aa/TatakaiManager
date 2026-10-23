package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.BuildStatus;

import java.util.UUID;

/** Recursos/criticalState null se RESOURCES oculta. */
public record CharacterSheetSummaryResponse(UUID id, UUID ownerUserId, String ownerName, String name,
        BuildStatus buildStatus, Integer currentHp, Integer effectiveHpMax, Integer currentSp, Integer spMax,
        Integer currentKp, Integer kpMax, Boolean criticalState) {}
