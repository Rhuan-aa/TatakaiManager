package com.tatakai.manager.martial.dto.request;

import com.tatakai.manager.martial.entity.TechniqueState;

import jakarta.validation.constraints.NotNull;

public record TechniqueStateRequest(@NotNull TechniqueState state) {}
