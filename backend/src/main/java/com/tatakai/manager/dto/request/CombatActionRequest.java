package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.CombatAction;
import com.tatakai.manager.entity.Guard;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CombatActionRequest(
        @NotNull UUID participantId,
        @NotNull CombatAction action,
        @Min(0) @Max(99) Integer spCost,
        Boolean slow,
        @Size(max = 100) @Pattern(regexp = "^[^<>]*$", message = "Caracteres < e > não são permitidos") String label,
        UUID targetParticipantId,
        Guard guard,
        UUID baseId,
        /** Estabilizar com kit médico ou conhecimento de medicina: vantagem */
        Boolean medical) {

    public CombatActionRequest(UUID participantId, CombatAction action, Integer spCost, Boolean slow, String label,
                               UUID targetParticipantId, Guard guard, UUID baseId) {
        this(participantId, action, spCost, slow, label, targetParticipantId, guard, baseId, null);
    }

    public CombatActionRequest(UUID participantId, CombatAction action, Integer spCost, Boolean slow, String label,
                               UUID targetParticipantId) {
        this(participantId, action, spCost, slow, label, targetParticipantId, null, null, null);
    }

    public CombatActionRequest(UUID participantId, CombatAction action, Integer spCost, Boolean slow, String label) {
        this(participantId, action, spCost, slow, label, null, null, null);
    }
}
