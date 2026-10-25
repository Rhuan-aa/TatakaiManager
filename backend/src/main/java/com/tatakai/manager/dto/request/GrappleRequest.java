package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.service.rules.GrappleRules;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Ação de agarre (§39). ESCAPE (quem está preso), PROJECT / LEG_TAKEDOWN / CONSTRICT / STRANGLE / MAINTAIN / RELEASE
 * (quem agarra, contra `targetParticipantId`). `attribute`: FOR ou DES na fuga (quando permitido); `strangleType` ao
 * estrangular; `twist`: torcer ao manter a Constrição.
 */
public record GrappleRequest(
        @NotNull UUID participantId,
        @NotNull Action action,
        UUID targetParticipantId,
        Attribute attribute,
        GrappleRules.StrangleType strangleType,
        Boolean twist) {

    public enum Action { ESCAPE, PROJECT, LEG_TAKEDOWN, CONSTRICT, STRANGLE, MAINTAIN, RELEASE }
}
