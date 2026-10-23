package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Os sete atributos da ficha (0..999). */
public record AttributesPayload(
        @NotNull @Min(0) @Max(999) Integer str,
        @NotNull @Min(0) @Max(999) Integer dex,
        @NotNull @Min(0) @Max(999) Integer con,
        @NotNull @Min(0) @Max(999) Integer intl,
        @NotNull @Min(0) @Max(999) Integer cha,
        @NotNull @Min(0) @Max(999) Integer men,
        @NotNull @Min(0) @Max(999) Integer tal
) {}
