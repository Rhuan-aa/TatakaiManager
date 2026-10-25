package com.tatakai.manager.build.dto.request;

import com.tatakai.manager.build.entity.BuildStep;

import jakarta.validation.constraints.NotNull;

public record BuildBackRequest(
        @NotNull BuildStep step
) {}
