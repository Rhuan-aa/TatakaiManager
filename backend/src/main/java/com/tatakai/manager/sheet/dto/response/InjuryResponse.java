package com.tatakai.manager.sheet.dto.response;

import com.tatakai.manager.sheet.entity.Attribute;
import com.tatakai.manager.sheet.entity.InjurySeverity;
import com.tatakai.manager.sheet.entity.InjuryType;

import java.time.Instant;
import java.util.UUID;

public record InjuryResponse(UUID id, InjuryType type, String region, InjurySeverity severity,
        int hpMaxReduction, int limiterReduction, Attribute affectedAttribute, boolean treated, Instant createdAt) {}
