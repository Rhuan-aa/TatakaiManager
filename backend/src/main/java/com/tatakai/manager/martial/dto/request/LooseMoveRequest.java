package com.tatakai.manager.martial.dto.request;

import com.tatakai.manager.catalog.entity.ArtBase;
import com.tatakai.manager.catalog.entity.ArtTechnique;
import com.tatakai.manager.catalog.entity.MoveKind;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Entrada do catálogo de avulsas: a montagem do tipo escolhido é obrigatória e conferida pela Criação Marcial. */
public record LooseMoveRequest(@NotNull MoveKind kind,
                               @NotBlank @Size(max = 100) @Pattern(regexp = "^[^<>]*$") String name,
                               Boolean visible, ArtTechnique technique, ArtBase base) {}
