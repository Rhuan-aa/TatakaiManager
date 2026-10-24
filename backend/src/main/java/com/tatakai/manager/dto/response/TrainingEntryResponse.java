package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.TrainingEntryStatus;

import java.time.Instant;
import java.util.UUID;

public record TrainingEntryResponse(UUID id, UUID timeSkipId, short dayNumber, String target, UUID techniqueId, int gain,
                                    boolean full, TrainingEntryStatus status, String note, Instant createdAt) {}
