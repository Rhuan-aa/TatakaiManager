package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.CombatClassification;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateEncounterRequest(
        @Size(min = 1, max = 100) @Pattern(regexp = "^[^<>]*$", message = "Caracteres < e > não são permitidos") String name,
        CombatClassification defaultClassification) {
}
