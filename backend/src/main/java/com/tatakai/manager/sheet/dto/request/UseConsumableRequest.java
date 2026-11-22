package com.tatakai.manager.sheet.dto.request;

import jakarta.validation.constraints.Pattern;

/** `option` só no Kit de Cutman: HP (2d6 HP, padrão) ou WOUNDS (fecha as Feridas). */
public record UseConsumableRequest(@Pattern(regexp = "^(HP|WOUNDS)$") String option) {
}
