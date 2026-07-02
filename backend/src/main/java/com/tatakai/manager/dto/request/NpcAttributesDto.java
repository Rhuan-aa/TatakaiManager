package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Atributos opcionais do NPC. Todos os campos podem ser nulos.
 */
public record NpcAttributesDto(
        @Min(0) @Max(999) Short forca,
        @Min(0) @Max(999) Short destreza,
        @Min(0) @Max(999) Short constituicao,
        @Min(0) @Max(999) Short mental,
        @Min(0) @Max(999) Short inteligencia,
        @Min(0) @Max(999) Short talento
) {}
