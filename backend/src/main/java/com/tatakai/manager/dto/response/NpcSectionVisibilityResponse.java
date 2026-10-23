package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.SheetSection;

public record NpcSectionVisibilityResponse(SheetSection section, boolean visible) {}
