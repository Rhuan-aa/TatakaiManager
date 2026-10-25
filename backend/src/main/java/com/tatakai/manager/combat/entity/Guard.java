package com.tatakai.manager.combat.entity;

/** Guarda de combate (spec §6.3). */
public enum Guard {
    NATURAL("natural"), INVERTED("invertida"), FRONTAL("frontal");

    private final String label;

    Guard(String label) { this.label = label; }

    /** Rótulo pt-BR para a linha do tempo. */
    public String label() { return label; }
}
