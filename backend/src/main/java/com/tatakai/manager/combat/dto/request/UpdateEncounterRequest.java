package com.tatakai.manager.combat.dto.request;

import com.tatakai.manager.combat.entity.CombatClassification;
import com.tatakai.manager.combat.entity.Floor;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateEncounterRequest(
        @Size(min = 1, max = 100) @Pattern(regexp = "^[^<>]*$", message = "Caracteres < e > não são permitidos") String name,
        CombatClassification defaultClassification,
        /** Chão do encontro (Arremesso/Cravar, §41). */
        com.tatakai.manager.combat.entity.Floor floor) {

    public UpdateEncounterRequest(String name, CombatClassification defaultClassification) {
        this(name, defaultClassification, null);
    }
}
