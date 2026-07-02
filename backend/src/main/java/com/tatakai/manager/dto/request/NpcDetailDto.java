package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Entrada de Conhecimento ou Traço do NPC — mesmo formato {nome, descrição}.
 * A lista é opcional, mas cada entrada precisa de um nome.
 */
public record NpcDetailDto(

        @NotBlank(message = "o nome é obrigatório")
        @Size(max = 100)
        @Pattern(regexp = "^[^<>]*$", message = "contém caracteres inválidos")
        String name,

        @Size(max = 2000)
        @Pattern(regexp = "^[^<>]*$", message = "descrição contém caracteres inválidos")
        String description

) {}
