package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.BodyType;
import com.tatakai.manager.entity.Dominance;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record BuildIdentityRequest(
        @NotNull BodyType bodyType,
        @NotNull Dominance dominance,
        @NotNull @DecimalMin("1.00") @DecimalMax("2.50") Double heightM,
        Double weightKg
) {}
