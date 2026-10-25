package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.CombatClassification;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateParticipantRequest(
        @Min(-99) @Max(999) Integer initiative,
        @Min(0) @Max(99) Integer paCurrent,
        @Min(0) @Max(99) Integer paBonusNext,
        @Min(0) @Max(99) Integer paLossNext,
        @Min(0) @Max(999) Integer orderIndex,
        CombatClassification classification,
        @Min(1) @Max(9) Integer team,
        @Min(0) @Max(10) Integer adrenaline,
        java.util.UUID mainArtId) {

    public UpdateParticipantRequest(Integer initiative, Integer paCurrent, Integer paBonusNext, Integer paLossNext,
                                    Integer orderIndex, CombatClassification classification, Integer team,
                                    Integer adrenaline) {
        this(initiative, paCurrent, paBonusNext, paLossNext, orderIndex, classification, team, adrenaline, null);
    }

    public UpdateParticipantRequest(Integer initiative, Integer paCurrent, Integer paBonusNext, Integer paLossNext,
                                    Integer orderIndex) {
        this(initiative, paCurrent, paBonusNext, paLossNext, orderIndex, null, null, null, null);
    }
}
