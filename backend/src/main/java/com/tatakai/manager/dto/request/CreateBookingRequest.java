package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateBookingRequest(

        @NotNull(message = "npcId é obrigatório")
        UUID npcId,

        @NotNull(message = "o dia é obrigatório")
        @Min(value = 1, message = "dia inválido")
        Short dayNumber,

        @NotNull(message = "o slot é obrigatório")
        @Min(value = 1, message = "o slot deve ser de 1 a 4")
        @Max(value = 4, message = "o slot deve ser de 1 a 4")
        Short slotNumber,

        @NotBlank(message = "o tipo de interação é obrigatório")
        String interactionName

) {}
