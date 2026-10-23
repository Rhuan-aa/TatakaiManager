package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record TraitChoiceRequest(
        @NotNull List<UUID> traitIds
) {}
