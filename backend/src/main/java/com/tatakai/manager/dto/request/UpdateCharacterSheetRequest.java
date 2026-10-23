package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.BodyType;
import com.tatakai.manager.entity.Dominance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCharacterSheetRequest(
        @NotBlank(message = "o nome é obrigatório")
        @Size(max = 100, message = "o nome deve ter no máximo 100 caracteres")
        @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos")
        String name,

        @NotNull(message = "o biotipo é obrigatório") BodyType bodyType,

        @NotNull(message = "a dominância é obrigatória") Dominance dominance,

        @NotNull(message = "a altura é obrigatória")
        @DecimalMin("1.00") @DecimalMax("2.50")
        Double heightM,

        /** null => SheetRules.initialWeight */
        Double weightKg,

        @NotNull(message = "os atributos são obrigatórios") @Valid AttributesPayload attributes
) {}
