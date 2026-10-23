package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.SheetSection;
import jakarta.validation.constraints.NotNull;

public record SetNpcSectionVisibilityRequest(@NotNull SheetSection section, @NotNull Boolean visible) {}
