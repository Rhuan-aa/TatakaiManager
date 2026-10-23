package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ItemPayload(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String name,
        @NotNull @Min(0) Integer quantity,
        @Size(max = 2000) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String description
) {}
