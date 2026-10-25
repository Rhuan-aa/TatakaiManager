package com.tatakai.manager.dto.response;

import java.util.UUID;

/** Evento interno; o listener AFTER_COMMIT publica {encounterId, type} em /topic/campaign/{cid}/combat. */
public record CombatUpdateMessage(UUID campaignId, UUID encounterId, String type) {
}
