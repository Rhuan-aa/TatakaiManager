package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.Attribute;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record FreePointsRequest(
        @NotNull Map<Attribute, Integer> freePoints
) {}
