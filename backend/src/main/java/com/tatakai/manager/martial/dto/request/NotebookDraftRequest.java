package com.tatakai.manager.martial.dto.request;

import com.tatakai.manager.catalog.entity.ArtBase;
import com.tatakai.manager.catalog.entity.ArtTechnique;
import com.tatakai.manager.catalog.entity.MoveKind;

import jakarta.validation.constraints.NotNull;

/** Rascunho do Caderno: `technique` se kind = TECHNIQUE, `base` se BASE. */
public record NotebookDraftRequest(@NotNull MoveKind kind, ArtTechnique technique, ArtBase base) {}
