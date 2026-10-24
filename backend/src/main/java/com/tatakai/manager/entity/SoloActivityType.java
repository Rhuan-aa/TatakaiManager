package com.tatakai.manager.entity;

/** Tipo de atividade solo que um jogador pode agendar num slot de TimeSkip sem NPC. */
public enum SoloActivityType {
    TREINO,
    ESTUDO,
    ACAO_GERAL,
    /** Repouso (RN-PROG-06): consolida treinos pendentes e recupera recursos ao avançar o dia. */
    REPOUSO
}
