package com.tatakai.manager.service.rules;

import java.util.List;

/** Condições de ambiente (capítulo de Condições, §33): Queimando, Congelando, Envenenado e Sufocando. Puro. */
public final class EnvironmentRules {

    /** Nível de uma condição com intensidade: dado de dano por turno, ND para sair, e se o dano vai no SP. */
    public record Level(String label, String dice, int nd, boolean sp) {}

    /** Queimando: Comum, Intenso, Avassalador (intensidade 1..3). Apagar: Ação Maior + DES contra o ND. */
    public static final List<Level> BURN = List.of(
            new Level("Comum", "1d4", 13, false), new Level("Intenso", "2d6", 15, false),
            new Level("Avassalador", "2d8", 17, false));

    /** Envenenado: Fraco (1d4 no SP), Moderado (1d6), Letal (2d6); CON contra o ND no fim do turno. */
    public static final List<Level> POISON = List.of(
            new Level("Fraco", "1d4", 12, true), new Level("Moderado", "1d6", 14, false),
            new Level("Letal", "2d6", 17, false));

    /** Envenenado termina após este número de sucessos. */
    public static final int POISON_CURE = 2;
    public static final String FREEZE_DAMAGE = "1d3";
    public static final String SUFFOCATE_SP = "1d6";

    private EnvironmentRules() {}

    /** Nível pela intensidade (null/fora da faixa → mais próximo). */
    public static Level level(List<Level> levels, Integer intensity) {
        int i = intensity == null ? 1 : intensity;
        return levels.get(Math.max(1, Math.min(levels.size(), i)) - 1);
    }

    /** Congelando: ND 14, +2 a cada turno seguido na condição (`tests` = testes já feitos). */
    public static int freezeNd(int tests) {
        return 14 + 2 * Math.max(0, tests);
    }
}
