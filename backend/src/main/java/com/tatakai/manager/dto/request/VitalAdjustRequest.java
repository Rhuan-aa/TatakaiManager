package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.VitalResource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VitalAdjustRequest(
        @NotNull VitalResource resource,
        @NotNull @Min(-9999) @Max(9999) Integer delta,
        @NotBlank @Size(max = 200) @Pattern(regexp = "^[^<>]*$", message = "Caracteres < e > não são permitidos") String reason) {
}
