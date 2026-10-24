package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.TechniqueState;
import jakarta.validation.constraints.NotNull;

public record TechniqueStateRequest(@NotNull TechniqueState state) {}
