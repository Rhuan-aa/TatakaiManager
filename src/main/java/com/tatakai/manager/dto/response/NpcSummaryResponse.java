package com.tatakai.manager.dto.response;

import java.util.UUID;

/**
 * Resumo de NPC na listagem de uma campanha.
 * Para o Mestre, {@code visible} indica o estado da visibilidade;
 * para o jogador, apenas NPCs visíveis são listados.
 */
public record NpcSummaryResponse(
        UUID id,
        String name,
        boolean visible
) {}
