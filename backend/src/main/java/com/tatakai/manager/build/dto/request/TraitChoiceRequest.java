package com.tatakai.manager.build.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record TraitChoiceRequest(
        @NotNull List<UUID> traitIds
) {}
