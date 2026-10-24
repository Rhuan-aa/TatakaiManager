package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.ArtBase;
import com.tatakai.manager.entity.ArtCategory;
import com.tatakai.manager.entity.ArtTechnique;
import jakarta.validation.constraints.NotNull;

/** Exatamente um de `technique` / `base`. */
public record AssemblyCheckRequest(@NotNull ArtCategory category, ArtTechnique technique, ArtBase base) {}
