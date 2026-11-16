package com.tatakai.manager.sheet.entity;

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
    ACTIVITY,
    HP,
    SP,
    KP,
    FATIGUE,
    CLANS,
    INTERACTIONS,
    ARTS,
    BASES,
    TECHNIQUES,
    RADAR,
    BODY_MAP,
    IMAGE;

    /** Seções que o Mestre configura na ficha de NPC, na ordem de exibição. */
    public static final java.util.List<SheetSection> NPC_SECTIONS = java.util.List.of(
            HP, SP, KP, FATIGUE, ATTRIBUTES, TRAITS, CLANS, INJURIES, INTERACTIONS, INVENTORY,
            ARTS, BASES, TECHNIQUES, RADAR, BODY_MAP, IMAGE);
}
