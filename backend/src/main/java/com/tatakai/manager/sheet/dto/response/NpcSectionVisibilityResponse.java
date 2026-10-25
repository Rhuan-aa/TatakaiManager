package com.tatakai.manager.sheet.dto.response;

import com.tatakai.manager.sheet.entity.SheetSection;

public record NpcSectionVisibilityResponse(SheetSection section, boolean visible) {}
