package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.KeikenRank;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InstinctPayload(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String name,
        @NotNull KeikenRank rank,
        @Size(max = 4000) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String description
) {}
