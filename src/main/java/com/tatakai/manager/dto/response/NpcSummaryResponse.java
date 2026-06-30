package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.InteractionType;

import java.util.Set;
import java.util.UUID;

/**
 * Resumo de NPC na listagem de uma campanha.
 * Para o Mestre, {@code visible} indica o estado da visibilidade;
 * para o jogador, apenas NPCs visíveis são listados.
 * {@code interactionTypes} permite à grade de slots oferecer só interações válidas.
 */
public record NpcSummaryResponse(
        UUID id,
        String name,
        boolean visible,
        Set<InteractionType> interactionTypes
) {}
