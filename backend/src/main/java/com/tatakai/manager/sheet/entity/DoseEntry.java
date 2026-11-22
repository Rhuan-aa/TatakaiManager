package com.tatakai.manager.sheet.entity;

/**
 * Dose tomada desde o último Repouso (§54). `count`: quantas vezes no "dia"; `masked`: Desgaste Físico escondido
 * pelo Analgésico (volta no Repouso); `spent`: o Energético já valeu num combate.
 */
public record DoseEntry(String key, int count, int masked, boolean spent) {
}
