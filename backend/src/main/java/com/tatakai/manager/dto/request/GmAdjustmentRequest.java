package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.GmAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record GmAdjustmentRequest(
        @NotNull GmAction action,
        String field,
        Integer value,
        @NotBlank @Size(max = 500) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String reason,
        @Valid InjuryPayload injury,
        @Valid ItemPayload item,
        UUID targetId
) {}
