package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SetCurrentDayRequest(
        @NotNull(message = "o dia é obrigatório")
        @Min(value = 1, message = "o dia deve ser no mínimo 1")
        Short currentDay
) {}
