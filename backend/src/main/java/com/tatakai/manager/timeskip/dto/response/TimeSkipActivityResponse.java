package com.tatakai.manager.timeskip.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TimeSkipActivityResponse(
        UUID id,
        UUID timeSkipId,
        String name,
        String description,
        short idlePointCost,
        Instant createdAt
) {}
