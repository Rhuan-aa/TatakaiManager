package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.MoveKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record LearnMoveRequest(@NotNull UUID characterArtId, @NotNull MoveKind kind, @NotBlank @Size(max = 100) String name) {}
