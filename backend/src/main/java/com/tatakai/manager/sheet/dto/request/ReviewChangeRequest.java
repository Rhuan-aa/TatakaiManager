package com.tatakai.manager.sheet.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewChangeRequest(
        @NotNull Boolean approved,
        @Size(max = 500) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String note
) {}
