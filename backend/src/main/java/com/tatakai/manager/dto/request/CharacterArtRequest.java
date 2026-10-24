package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CharacterArtRequest(@NotNull UUID artId) {}
