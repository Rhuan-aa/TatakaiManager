package com.tatakai.manager.sheet.dto.request;

import com.tatakai.manager.sheet.entity.SheetSection;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** Só as chaves enviadas mudam. */
public record UpdateNpcSheetVisibilityRequest(@NotNull Map<SheetSection, Boolean> sections) {}
