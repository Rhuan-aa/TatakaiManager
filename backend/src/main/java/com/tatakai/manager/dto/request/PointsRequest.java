package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.NotNull;

public record PointsRequest(@NotNull Integer points) {}
