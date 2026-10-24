package com.tatakai.manager.entity;

/** Patente Keiken pelo EL total (RN-PROG-08): Novato, Calejado, Veterano, Histórico, Lenda, Mitológico, Deus-Dragão. */
public enum KeikenRank {
    NOVICE(0), SEASONED(5), VETERAN(15), HISTORIC(30), LEGEND(55), MYTHIC(80), DRAGON_GOD(120);

    public final int minEl;

    KeikenRank(int minEl) {
        this.minEl = minEl;
    }
}
