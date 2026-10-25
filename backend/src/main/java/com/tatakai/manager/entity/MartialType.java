package com.tatakai.manager.entity;

/** Tipo marcial desarmado (spec §6.2). */
public enum MartialType {
    STRIKING("Trocação"), TAKEDOWN("Derrubada"), SWEEP("Raspagem"), GENERAL("Geral");

    private final String label;

    MartialType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
