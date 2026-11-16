package com.tatakai.manager.sheet.dto.request;

import com.tatakai.manager.sheet.entity.BodyPart;
import com.tatakai.manager.sheet.entity.GmAction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record GmAdjustmentRequest(
        @NotNull GmAction action,
        String field,
        Integer value,
        @NotBlank @Size(max = 500) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String reason,
        @Valid InjuryPayload injury,
        @Valid ItemPayload item,
        UUID targetId,
        /** SET_MISSING_PART: parte do corpo (obrigatória nessa ação). */
        BodyPart bodyPart,
        /** SET_MISSING_PART: true = perdida, false/ausente = restaurada. */
        Boolean missing
) {
    public GmAdjustmentRequest(GmAction action, String field, Integer value, String reason, InjuryPayload injury,
                               ItemPayload item, UUID targetId) {
        this(action, field, value, reason, injury, item, targetId, null, null);
    }
}
