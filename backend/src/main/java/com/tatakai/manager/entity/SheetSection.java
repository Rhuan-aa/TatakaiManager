package com.tatakai.manager.entity;

/**
 * Seções da ficha de um personagem (jogador) que podem ser ocultadas dos demais.
 * LOGS, ATTRIBUTES, KNOWLEDGE e TRAITS vêm das fichas textuais; RESOURCES, INJURIES,
 * INVENTORY e ACTIVITY pertencem à ficha digital. Só acrescente valores (gravado como STRING).
 */
public enum SheetSection {
    LOGS,
    ATTRIBUTES,
    KNOWLEDGE,
    TRAITS,
    RESOURCES,
    INJURIES,
    INVENTORY,
    ACTIVITY
}
