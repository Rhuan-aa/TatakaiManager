package com.tatakai.manager.sheet.dto.request;

import com.tatakai.manager.sheet.entity.Attribute;
import com.tatakai.manager.sheet.entity.InjurySeverity;
import com.tatakai.manager.sheet.entity.InjuryType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InjuryPayload(
        @NotNull InjuryType type,
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String region,
        @NotNull InjurySeverity severity,
        /** usado só em BLUNT */
        @Min(0) Integer damage,
        Attribute affectedAttribute
) {}
