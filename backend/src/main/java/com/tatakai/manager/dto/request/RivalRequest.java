package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RivalRequest(
        @Size(max = 100) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String art,
        @Size(max = 30) List<@Size(max = 100) @Pattern(regexp = "^[^<>]*$") String> knownTechniques
) {}
