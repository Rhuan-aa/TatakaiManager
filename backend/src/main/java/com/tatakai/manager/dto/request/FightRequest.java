package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.FightClassification;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fechamento de luta pelo Mestre: um item por jogador que lutou. */
public record FightRequest(
        @Size(max = 500) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String note,
        @NotEmpty @Valid List<Participant> participants
) {
    public record Participant(
            @NotNull UUID sheetId,
            @NotNull FightClassification classification,
            /** Nomes dos Marcos confirmados (catálogo); só 4 contam. */
            List<String> milestones,
            /** characterArtId → EL usado como pontos de graduação (soma ≤ EL da luta). */
            Map<UUID, Integer> artEl,
            /** Oponente para o Registro de Rivais (opcional). */
            @Valid RivalRef rival
    ) {}

    public record RivalRef(
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String name,
            UUID npcId
    ) {}
}
