package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.SheetSection;

public record SectionVisibilityResponse(
        SheetSection section,
        boolean hiddenByMaster,
        boolean hiddenBySelf,
        /** Se a seção é visível para quem está consultando (Mestre e dono sempre veem). */
        boolean visibleToMe
) {}
