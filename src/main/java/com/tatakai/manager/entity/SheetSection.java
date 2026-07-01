package com.tatakai.manager.entity;

/**
 * Seções da ficha de um personagem (jogador) que podem ser ocultadas dos demais.
 * Hoje só LOGS tem conteúdo; as demais existem para as fichas futuras.
 */
public enum SheetSection {
    LOGS,
    ATTRIBUTES,
    KNOWLEDGE,
    TRAITS
}
