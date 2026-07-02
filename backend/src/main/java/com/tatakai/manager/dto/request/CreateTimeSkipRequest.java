package com.tatakai.manager.dto.request;

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
        Short totalDays

) {}
