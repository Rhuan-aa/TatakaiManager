package com.tatakai.manager.catalog.dto.response;

import com.tatakai.manager.progression.entity.KeikenRank;

import java.util.UUID;

public record InstinctResponse(UUID id, UUID campaignId, String name, KeikenRank rank, String description) {}
