package com.tatakai.manager.combat.dto.request;

import com.tatakai.manager.combat.entity.Hysteria;

import jakarta.validation.constraints.NotNull;

public record SetHysteriaRequest(@NotNull Hysteria hysteria) {
}
