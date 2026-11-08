package com.tatakai.manager.timeskip.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewSubmissionRequest(
        @NotNull Boolean approved,
        @Size(max = 500) @Pattern(regexp = "^[^<>]*$", message = "não pode conter < ou >") String note) {}
