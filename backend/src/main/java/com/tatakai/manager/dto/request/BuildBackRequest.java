package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.BuildStep;
import jakarta.validation.constraints.NotNull;

public record BuildBackRequest(
        @NotNull BuildStep step
) {}
