package com.tatakai.manager.martial.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ForgeStartRequest(@NotNull UUID draftId) {}
