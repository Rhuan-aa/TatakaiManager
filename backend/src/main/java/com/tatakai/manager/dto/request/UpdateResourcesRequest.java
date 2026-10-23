package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateResourcesRequest(
        @Min(0) Integer currentHp,
        @Min(0) Integer currentSp,
        @Min(0) Integer currentKp,
        @Size(max = 500) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String reason
) {}
