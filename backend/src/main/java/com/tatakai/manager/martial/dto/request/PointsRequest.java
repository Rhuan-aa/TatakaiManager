package com.tatakai.manager.martial.dto.request;

import jakarta.validation.constraints.NotNull;

public record PointsRequest(@NotNull Integer points) {}
