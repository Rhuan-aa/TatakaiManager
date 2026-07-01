package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Tipo de interação de um NPC: nome, descrição (opcional) e custo em pontos de treino.
 */
public record NpcInteractionDto(

        @NotBlank(message = "o nome da interação é obrigatório")
        @Size(max = 100)
        @Pattern(regexp = "^[^<>]*$", message = "interação contém caracteres inválidos")
        String name,

        @Size(max = 2000)
        @Pattern(regexp = "^[^<>]*$", message = "descrição contém caracteres inválidos")
        String description,

        @NotNull(message = "o custo em pontos de treino é obrigatório")
        @Min(value = 0, message = "o custo não pode ser negativo")
        @Max(value = 9999, message = "o custo é muito alto")
        Short trainPointCost

) {}
