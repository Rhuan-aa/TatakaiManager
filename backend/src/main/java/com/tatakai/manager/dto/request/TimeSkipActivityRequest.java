package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Atividade solo customizada de um TimeSkip (ex.: "Reconstrução da vila"), cadastrada
 * pelo Mestre com nome, descrição e custo em pontos de ócio fixos.
 */
public record TimeSkipActivityRequest(

        @NotBlank(message = "o nome é obrigatório")
        @Size(max = 100, message = "o nome deve ter no máximo 100 caracteres")
        @Pattern(regexp = "^[^<>]*$", message = "nome contém caracteres inválidos")
        String name,

        @NotBlank(message = "a descrição é obrigatória")
        @Size(max = 2000, message = "a descrição deve ter no máximo 2000 caracteres")
        @Pattern(regexp = "^[^<>]*$", message = "descrição contém caracteres inválidos")
        String description,

        @NotNull(message = "o custo em pontos de ócio é obrigatório")
        @Min(value = 0, message = "o custo não pode ser negativo")
        @Max(value = 9999, message = "o custo é muito alto")
        Short idlePointCost

) {}
