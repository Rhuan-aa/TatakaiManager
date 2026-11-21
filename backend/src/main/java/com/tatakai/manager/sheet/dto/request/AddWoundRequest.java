package com.tatakai.manager.sheet.dto.request;

import com.tatakai.manager.sheet.entity.BodyPart;
import com.tatakai.manager.sheet.entity.InjurySeverity;
import com.tatakai.manager.sheet.entity.WoundType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Marca desenhada no boneco. {@code injury} preenchido = "seguir a regra de Ferimentos Permanentes": cria também o
 * ferimento (mesma conta do ADD_INJURY) na mesma parte. Sem ele, a marca é só visual.
 */
public record AddWoundRequest(
        @NotNull BodyPart part,
        @NotNull WoundType type,
        @NotNull WoundGeometry geometry,
        InjurySeverity severity,
        @Valid InjuryPayload injury
) {}
