package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.KeikenRank;

import java.util.UUID;

public record InstinctResponse(UUID id, UUID campaignId, String name, KeikenRank rank, String description) {}
