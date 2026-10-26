package com.tatakai.manager.timeskip.entity;

/**
 * Modo de passagem de tempo na Agenda:
 * - IDLE_PERIOD: Período Ocioso do dia a dia da campanha (os 4 pontos de ócio normais de um dia).
 * - TIMESKIP: Salto temporal / fora de cena (semanas ou meses de treino e recuperação).
 */
public enum TimeSkipMode {
    IDLE_PERIOD,
    TIMESKIP
}
