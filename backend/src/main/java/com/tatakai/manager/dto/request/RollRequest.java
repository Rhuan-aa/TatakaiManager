package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RollRequest(
        @NotBlank @Pattern(regexp = "^\\d{1,2}d(4|6|8|10|12|20|100)([+-]\\d{1,3})?$") String expression,
        @NotBlank @Size(max = 200) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String purpose
) {}
