package com.tatakai.manager.service.rules;

import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.AttributeBand;
import com.tatakai.manager.entity.BodyType;
import com.tatakai.manager.entity.FatigueBand;
import com.tatakai.manager.entity.InjurySeverity;
import com.tatakai.manager.entity.InjuryType;
import com.tatakai.manager.entity.PermanentInjury;
import com.tatakai.manager.entity.ResourceType;

import java.util.List;

/** Regras puras da ficha (sem Spring). Arredondamento sempre para baixo (floor). */
public final class SheetRules {

    private SheetRules() {
    }

    /** RN-ATR-02: null para atributo 0 (Estado Crítico). */
    public static Integer mod(int v) {
        if (v <= 0) return null;
        if (v <= 3) return -4;
        if (v <= 6) return -2;
        if (v <= 9) return 0;
        return v / 10;
    }

    public static int modQ(int v) {
        Integer m = mod(v);
        return m == null ? 0 : Math.max(0, m);
    }

    public static int half(int m) {
        return m > 0 ? m / 2 : m;
    }

    public static AttributeBand band(int v) {
        if (v <= 0) return AttributeBand.COLLAPSE;
        if (v <= 3) return AttributeBand.INEPT;
        if (v <= 6) return AttributeBand.DEFICIENT;
        if (v <= 9) return AttributeBand.UNTRAINED;
        return AttributeBand.FUNCTIONAL;
    }

    /** RN-ATR-04/05: TAL não tem limite. adjustment = ajuste de traço/clã/Mestre (somado). */
    public static int attributeLimiter(Attribute a, int tal, BodyType bt, int injuryReduction, int adjustment) {
        if (a == Attribute.TAL) return Integer.MAX_VALUE;
        int adj = 0;
        if (bt == BodyType.ENDOMORPH) {
            if (a == Attribute.CON) adj = 10;
            else if (a == Attribute.DEX) adj = -5;
        } else if (bt == BodyType.MESOMORPH) {
            if (a == Attribute.STR || a == Attribute.CON) adj = 5;
        } else if (bt == BodyType.ECTOMORPH) {
            if (a == Attribute.DEX) adj = 10;
            else if (a == Attribute.CON) adj = -5;
        }
        return 40 + tal / 2 + adj - injuryReduction + adjustment;
    }

    public static int statusLimiter(ResourceType r, int tal, BodyType bt, int adjustment) {
        if (r == ResourceType.KP) return 8 + adjustment;
        int adj = 0;
        if (r == ResourceType.HP) {
            if (bt == BodyType.ENDOMORPH) adj = 10;
            else if (bt == BodyType.MESOMORPH) adj = 5;
        } else {
            if (bt == BodyType.MESOMORPH) adj = 5;
            else if (bt == BodyType.ECTOMORPH) adj = 10;
        }
        return 60 + tal / 2 + adj + adjustment;
    }

    public static int initialHpMax(int con) {
        return 10 + con;
    }

    public static int initialSpMax(int con) {
        return 5 + con / 2;
    }

    public static int initialKpMax(int con) {
        return 3 + modQ(con);
    }

    /** RN-ATR-08: peso inicial com clamp pelo índice mínimo/máximo do tipo corporal. */
    public static double initialWeight(double heightM, BodyType bt, int con) {
        int base;
        int minIdx;
        int maxIdx;
        switch (bt) {
            case ENDOMORPH -> { base = 50; minIdx = 20; maxIdx = 40; }
            case MESOMORPH -> { base = 40; minIdx = 17; maxIdx = 33; }
            default -> { base = 30; minIdx = 15; maxIdx = 26; }
        }
        double w = (heightM - 0.20) * (base + con / 4.0);
        double h2 = heightM * heightM;
        return Math.max(h2 * minIdx, Math.min(h2 * maxIdx, w));
    }

    public static int weightMod(double kg) {
        if (kg < 35) return -2;
        if (kg < 50) return -1;
        if (kg < 65) return 0;
        if (kg < 80) return 1;
        if (kg < 95) return 2;
        if (kg < 110) return 3;
        if (kg < 125) return 4;
        if (kg < 140) return 5;
        return 5 + (int) Math.floor((kg - 125) / 15);
    }

    public static int carryKg(int str) {
        return 3 * str;
    }

    /** HP máximo efetivo: hpMax menos as reduções dos ferimentos, nunca abaixo de 1. */
    public static int effectiveHpMax(int hpMax, List<PermanentInjury> injuries) {
        int sum = injuries.stream().mapToInt(PermanentInjury::getHpMaxReduction).sum();
        return Math.max(1, hpMax - sum);
    }

    public static boolean criticalState(int currentHp, int effectiveHpMax, int... attributes) {
        if (currentHp <= effectiveHpMax / 4) return true;
        for (int a : attributes) {
            if (a == 0) return true;
        }
        return false;
    }

    public static FatigueBand fatigueBand(int points) {
        if (points <= 2) return FatigueBand.RESTED;
        if (points <= 5) return FatigueBand.TIRED;
        if (points <= 8) return FatigueBand.DRAINED;
        if (points <= 11) return FatigueBand.EXHAUSTED;
        return FatigueBand.COLLAPSE;
    }

    public static int injuryHpReduction(InjurySeverity s, InjuryType t, int hpMax, Integer damage) {
        if (t == InjuryType.BLUNT) {
            return (damage == null ? 0 : damage) / 2;
        }
        int pct = switch (s) {
            case MINOR -> 10;
            case MODERATE -> 25;
            case SEVERE -> 45;
            case LETHAL -> 100;
        };
        return hpMax * pct / 100;
    }

    public static int injuryLimiterReduction(InjurySeverity s) {
        return switch (s) {
            case MINOR -> 5;
            case MODERATE -> 10;
            case SEVERE -> 15;
            case LETHAL -> 0;
        };
    }
}
