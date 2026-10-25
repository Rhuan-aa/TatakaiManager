package com.tatakai.manager.dto.response;

import java.util.UUID;

/**
 * Aviso genérico de mudança na campanha (canal `campaign`, publicado após o commit): o cliente refaz o GET.
 * type: TIMESKIP (criar, encerrar, excluir, mudar dia/local) ou NPCS (associar, remover, visibilidade, Agenda, edição).
 */
public record CampaignUpdateMessage(UUID campaignId, String type, UUID id) {}
