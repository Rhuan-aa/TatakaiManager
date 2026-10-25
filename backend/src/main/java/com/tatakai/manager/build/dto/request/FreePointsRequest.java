package com.tatakai.manager.build.dto.request;

import com.tatakai.manager.sheet.entity.Attribute;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record FreePointsRequest(
        @NotNull Map<Attribute, Integer> freePoints
) {}
