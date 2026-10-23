package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReopenBuildRequest(
        @NotBlank @Size(max = 500) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String reason
) {}
