package com.tatakai.manager.progression.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InstinctChoiceRequest(@NotNull UUID instinctId) {}
