package com.tatakai.manager.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RollChannelResponse(UUID id, UUID campaignId, String name, List<UUID> memberIds, Instant createdAt) {}
