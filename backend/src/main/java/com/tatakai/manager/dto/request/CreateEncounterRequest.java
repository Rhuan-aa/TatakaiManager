package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.CombatClassification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEncounterRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[^<>]*$", message = "Caracteres < e > não são permitidos") String name,
        CombatClassification defaultClassification) {
}
