package com.tatakai.manager.timeskip.dto.response;

import java.util.UUID;

/** Projeção de Desgaste Físico se o período fosse encerrado agora (só dias sem Repouso; §51.2). */
public record FatiguePreviewResponse(UUID userId, String userName, int physicalFatigue, int mentalFatigue,
                                     int unrestedDays, int projectedPhysical) {
}
