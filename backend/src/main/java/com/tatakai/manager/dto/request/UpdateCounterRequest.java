package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCounterRequest(@NotNull @Min(0) @Max(99) Integer value) {
}
