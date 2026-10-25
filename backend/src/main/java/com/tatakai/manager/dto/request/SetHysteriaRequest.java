package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.Hysteria;
import jakarta.validation.constraints.NotNull;

public record SetHysteriaRequest(@NotNull Hysteria hysteria) {
}
