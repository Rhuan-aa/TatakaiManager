package com.tatakai.manager.sheet.dto.request;

import com.tatakai.manager.sheet.entity.SheetSection;

import jakarta.validation.constraints.NotNull;

public record SetSectionVisibilityRequest(
        @NotNull(message = "a seção é obrigatória")
        SheetSection section,

        @NotNull(message = "informe se a seção deve ficar oculta")
        Boolean hidden
) {}
