package com.tatakai.manager.dto.response;

import com.tatakai.manager.dto.request.NpcInteractionDto;

import java.util.List;
import java.util.UUID;

/**
 * Resumo de NPC na listagem de uma campanha.
 * Para o Mestre, {@code visible} indica o estado da visibilidade;
 * para o jogador, apenas NPCs visíveis são listados.
 * {@code interactions} permite à grade de slots oferecer as interações válidas
 * (com nome e custo em pontos de treino).
 */
public record NpcSummaryResponse(
        UUID id,
        String name,
        boolean visible,
        List<NpcInteractionDto> interactions
) {}
