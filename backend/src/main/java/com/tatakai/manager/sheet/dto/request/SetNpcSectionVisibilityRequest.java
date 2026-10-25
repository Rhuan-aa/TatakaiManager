package com.tatakai.manager.sheet.dto.request;

import com.tatakai.manager.sheet.entity.SheetSection;

import jakarta.validation.constraints.NotNull;

public record SetNpcSectionVisibilityRequest(@NotNull SheetSection section, @NotNull Boolean visible) {}
