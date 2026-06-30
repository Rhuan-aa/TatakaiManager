package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.NotNull;

public record SetVisibilityRequest(
        @NotNull(message = "informe a visibilidade")
        Boolean visible
) {}
