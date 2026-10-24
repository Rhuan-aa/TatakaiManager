package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.MilestoneCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FightMilestonePayload(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String name,
        @NotNull MilestoneCategory category,
        @Size(max = 4000) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String description,
        @Pattern(regexp = "^CRITICAL_STATE$", message = "gatilho inválido") String autoTrigger,
        Boolean grantsArtPoint,
        Boolean seriousOnly,
        @Min(1) @Max(4) Integer minOtherDisadvantage
) {}
