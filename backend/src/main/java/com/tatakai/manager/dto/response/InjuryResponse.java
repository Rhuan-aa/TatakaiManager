package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.InjurySeverity;
import com.tatakai.manager.entity.InjuryType;

import java.time.Instant;
import java.util.UUID;

public record InjuryResponse(UUID id, InjuryType type, String region, InjurySeverity severity,
        int hpMaxReduction, int limiterReduction, Attribute affectedAttribute, boolean treated, Instant createdAt) {}
