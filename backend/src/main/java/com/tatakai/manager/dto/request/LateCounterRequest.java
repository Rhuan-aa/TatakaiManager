package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.BasicAttack;
import com.tatakai.manager.entity.RollSourceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record LateCounterRequest(
        @NotNull RollSourceType sourceType,
        UUID sourceId,
        @Size(max = 100) @Pattern(regexp = "^[^<>]*$") String variation,
        BasicAttack basicAttack) {
}
