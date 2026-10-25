package com.tatakai.manager.entity;

/** Ataques básicos do livro: dado, bônus fixo de dano e penalidade de acerto. */
public enum BasicAttack {
    SOCO("Soco", 1, 2, 0, 0),
    CHUTE("Chute", 1, 4, 0, 0),
    COTOVELADA("Cotovelada", 1, 3, 0, 0),
    JOELHADA("Joelhada", 1, 4, 1, -2), // −2 no acerto, exceto contra alvo Agarrado ou em Clinch
    CABECADA("Cabeçada", 1, 4, 0, 0),
    AGARRAO("Agarrão", 1, 2, 1, 0);

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
}
