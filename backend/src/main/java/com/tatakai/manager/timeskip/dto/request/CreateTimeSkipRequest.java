package com.tatakai.manager.timeskip.dto.request;

import com.tatakai.manager.timeskip.entity.TimeSkipMode;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTimeSkipRequest(

        @NotBlank(message = "nome é obrigatório")
        @Size(max = 100)
        @Pattern(regexp = "^[^<>]*$", message = "nome contém caracteres inválidos")
        String name,

        @NotNull(message = "número de dias é obrigatório")
        @Min(value = 1, message = "o TimeSkip deve ter ao menos 1 dia")
        @Max(value = 365, message = "o TimeSkip não pode exceder 365 dias")
        Short totalDays,

        TimeSkipMode mode,

        @Min(value = 1, message = "mínimo de 1 ponto de ócio por dia")
        @Max(value = 4, message = "máximo de 4 pontos de ócio por dia")
        Short slotsPerDay

) {
    public CreateTimeSkipRequest(String name, Short totalDays) {
        this(name, totalDays, null, null);
    }

    public CreateTimeSkipRequest(String name, Short totalDays, TimeSkipMode mode) {
        this(name, totalDays, mode, null);
    }
}

