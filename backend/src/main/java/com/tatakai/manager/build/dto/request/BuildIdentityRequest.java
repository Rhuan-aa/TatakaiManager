package com.tatakai.manager.build.dto.request;

import com.tatakai.manager.sheet.entity.BodyType;
import com.tatakai.manager.sheet.entity.Dominance;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record BuildIdentityRequest(
        @NotNull BodyType bodyType,
        @NotNull Dominance dominance,
        @NotNull @DecimalMin("1.00") @DecimalMax("2.50") Double heightM,
        Double weightKg
) {}
