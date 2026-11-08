package com.tatakai.manager.martial.dto.request;

import com.tatakai.manager.catalog.entity.Graduation;
import com.tatakai.manager.catalog.entity.MoveKind;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Técnica/base avulsa. Com `artId`, copia o item da arte do catálogo; sem `artId`, é um item livre (sem arte) e usa
 * `technique`/`base` (montada na hora, conferida pelas regras de Criação Marcial) ou `graduation` (padrão genérica), `complexity` (padrão 1) e `trainingPoints` (padrão 3 × complexidade).
 */
public record LearnLooseRequest(UUID artId, @NotNull MoveKind kind, @NotBlank @Size(max = 100) String name,
                                Graduation graduation, @Min(0) @Max(99) Integer complexity,
                                @Min(0) @Max(999) Integer trainingPoints,
                                com.tatakai.manager.catalog.entity.ArtTechnique technique,
                                com.tatakai.manager.catalog.entity.ArtBase base) {
    public LearnLooseRequest(UUID artId, MoveKind kind, String name) {
        this(artId, kind, name, null, null, null, null, null);
    }
}
