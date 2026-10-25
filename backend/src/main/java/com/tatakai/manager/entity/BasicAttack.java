package com.tatakai.manager.entity;

/** Ataques básicos do livro: dado (0 lados = sem dano), bônus fixo de dano e penalidade de acerto. */
public enum BasicAttack {
    SOCO("Soco", 1, 2, 0, 0),
    CHUTE("Chute", 1, 4, 0, 0),
    COTOVELADA("Cotovelada", 1, 3, 0, 0),
    JOELHADA("Joelhada", 1, 4, 1, -2), // −2 no acerto, exceto contra alvo Agarrado ou em Clinch
    CABECADA("Cabeçada", 1, 4, 0, 0),
    AGARRAO("Agarrão", 1, 2, 0, 0), // dano só se apertar/torcer: 1d2 + mod. de Força
    MORDIDA("Mordida", 1, 2, 0, 0), // sem Força no dano; Ação Menor
    EMPURRAO("Empurrão", 0, 0, 0, 0), // sem dano; Ação Menor; FOR do alvo contra o acerto
    ENCONTRAO("Encontrão", 0, 0, 0, 0), // sem dano; exige 3 m em linha reta; FOR/DES contra acerto + mod. de Peso
    PISAO("Pisão", 1, 4, 0, 0), // só contra alvo Derrubado; + mod. de Peso no dano
    ARMA("Ataque com arma", 0, 0, 0, 0); // dano da arma em mãos (sourceId = item do inventário, §42)

    public final String label;
    public final int count;
    public final int sides;
    public final int flatDamage;
    public final int hitPenalty;

    BasicAttack(String label, int count, int sides, int flatDamage, int hitPenalty) {
        this.label = label;
        this.count = count;
        this.sides = sides;
        this.flatDamage = flatDamage;
        this.hitPenalty = hitPenalty;
    }

    public boolean dealsDamage() {
        return sides > 0;
    }

    /** Golpe do próprio corpo (não é o ataque com arma). */
    public boolean unarmed() {
        return this != ARMA;
    }

    /** Sem a Força no dano (Mordida, e os que não causam dano). */
    public boolean noStrength() {
        return this == MORDIDA || !dealsDamage();
    }

    /** Cabem numa Ação Menor Ofensiva sem Técnica Rápida. */
    public boolean quick() {
        return this == SOCO || this == MORDIDA || this == EMPURRAO;
    }
}
