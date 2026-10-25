package com.tatakai.manager.catalog.dto.request;

import com.tatakai.manager.catalog.entity.ArtBase;
import com.tatakai.manager.catalog.entity.ArtCategory;
import com.tatakai.manager.catalog.entity.ArtTechnique;

import jakarta.validation.constraints.NotNull;

/** Exatamente um de `technique` / `base`. */
public record AssemblyCheckRequest(@NotNull ArtCategory category, ArtTechnique technique, ArtBase base) {}
