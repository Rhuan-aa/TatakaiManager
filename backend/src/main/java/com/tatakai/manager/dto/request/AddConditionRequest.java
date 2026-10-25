package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.ConditionExpiry;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AddConditionRequest(
        @NotBlank @Size(max = 40) String key,
        ConditionExpiry expiresAt,
        @Min(1) @Max(99) Integer turnsRemaining,
        @Min(1) @Max(99) Integer intensity,
        UUID sourceParticipantId,
        @Size(max = 200) @Pattern(regexp = "^[^<>]*$", message = "Caracteres < e > não são permitidos") String note) {
}
