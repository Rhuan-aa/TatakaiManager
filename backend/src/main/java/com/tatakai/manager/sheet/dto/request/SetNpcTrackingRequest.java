package com.tatakai.manager.sheet.dto.request;

import jakarta.validation.constraints.NotNull;

/** Liga/desliga Cansaço e PS na ficha de NPC (§53.2-6). */
public record SetNpcTrackingRequest(@NotNull Boolean enabled) {}
