package com.tatakai.manager.combat.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Encadear (§41): técnica que substitui o golpe abortado. */
public record ChainAttackRequest(
        @NotNull UUID techniqueId,
        @Size(max = 100) @Pattern(regexp = "^[^<>]*$") String variation) {
}
