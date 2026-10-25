package com.tatakai.manager.sheet.dto.request;

import com.tatakai.manager.sheet.entity.ItemLocation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ItemPayload(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String name,
        @NotNull @Min(0) Integer quantity,
        @Size(max = 2000) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String description,
        /** peso unitário em kg; null = 0 */
        @DecimalMin("0") @DecimalMax("9999") Double weightKg,
        /** chave do catálogo de armas / equipamentos / consumíveis (§42–§44); null = item comum */
        @Size(max = 40) String weaponKey,
        @Size(max = 40) String equipmentKey,
        @Size(max = 40) String consumableKey,
        com.tatakai.manager.sheet.entity.ItemLocation location
) {
    public ItemPayload(String name, Integer quantity, String description, Double weightKg) {
        this(name, quantity, description, weightKg, null, null, null, null);
    }

    public double weight() {
        return weightKg == null ? 0 : weightKg;
    }
}
