package com.tatakai.manager.entity;

import java.util.List;

/** Parcela de uma rolagem. part = HIT|DAMAGE; dice != null só em parcelas de dado (value = soma). */
public record RollPart(String part, String source, int value, List<Integer> dice, boolean edited) {
    public RollPart withValue(int v) {
        return new RollPart(part, source, v, dice, true);
    }
}
