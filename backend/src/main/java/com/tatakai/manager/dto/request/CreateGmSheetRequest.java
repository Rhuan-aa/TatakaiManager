package com.tatakai.manager.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Ficha instantânea do Mestre (§26.11): vira um NPC só com ficha, fora da Agenda. */
public record CreateGmSheetRequest(
        @NotNull(message = "a ficha é obrigatória") @Valid CreateCharacterSheetRequest sheet,
        /** true = jogadores veem o NPC e todas as seções da ficha */
        boolean visible
) {}
