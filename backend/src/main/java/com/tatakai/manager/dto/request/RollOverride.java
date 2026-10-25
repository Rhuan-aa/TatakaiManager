package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Substitui o valor da parcela não-dado com este `source` (ou adiciona uma parcela HIT nova). */
public record RollOverride(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[^<>]*$") String source,
        int value) {}
