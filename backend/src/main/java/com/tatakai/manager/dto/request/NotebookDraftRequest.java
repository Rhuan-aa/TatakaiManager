package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.ArtBase;
import com.tatakai.manager.entity.ArtTechnique;
import com.tatakai.manager.entity.MoveKind;
import jakarta.validation.constraints.NotNull;

/** Rascunho do Caderno: `technique` se kind = TECHNIQUE, `base` se BASE. */
public record NotebookDraftRequest(@NotNull MoveKind kind, ArtTechnique technique, ArtBase base) {}
