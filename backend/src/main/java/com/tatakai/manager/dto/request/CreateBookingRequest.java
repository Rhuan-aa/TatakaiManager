package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.SoloActivityType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Agendamento de um slot: com NPC ({@code npcId} + {@code interactionName}), atividade
 * solo fixa ({@code soloActivityType} + {@code description}) ou atividade solo
 * customizada do TimeSkip ({@code activityId}) — mutuamente exclusivos, validado em
 * {@code BookingService}.
 */
public record CreateBookingRequest(

        UUID npcId,

        @NotNull(message = "o dia é obrigatório")
        @Min(value = 1, message = "dia inválido")
        Short dayNumber,

        @NotNull(message = "o slot é obrigatório")
        @Min(value = 1, message = "o slot deve ser de 1 a 4")
        @Max(value = 4, message = "o slot deve ser de 1 a 4")
        Short slotNumber,

        String interactionName,

        SoloActivityType soloActivityType,

        UUID activityId,

        String description

) {}
