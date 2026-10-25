package com.tatakai.manager.catalog.entity;

/** Tipo marcial desarmado ou armado (spec §6.2). */
public enum MartialType {
    STRIKING("Trocação"), TAKEDOWN("Derrubada"), SWEEP("Raspagem"),
    BLADE("Lâmina"), BLUNT("Contundente"), PIERCING("Perfurante"),
    GUNPOWDER("Pólvora"), RANGED_CRAFT("Artesanais"),
    GENERAL("Geral");

    private final String label;

    MartialType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
