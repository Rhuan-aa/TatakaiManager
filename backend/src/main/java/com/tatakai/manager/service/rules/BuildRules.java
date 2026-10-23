package com.tatakai.manager.service.rules;

import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.BuildGains;
import com.tatakai.manager.entity.Dominance;
import com.tatakai.manager.entity.TraitRarity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Regras puras da Construção Inicial (sem Spring). Arredondamento sempre para baixo (floor). */
public final class BuildRules {

    private BuildRules() {
    }

    public record AttributeLayer(int base, int clanFlat, int traitFlat, int percent,
                                 int afterPercent, int freePoints, int finalValue) {
    }

    public record ResourceMaxes(int hpMax, int spMax, int kpMax) {
    }

    /** 1–40 C, 41–70 R, 71–90 E, 91–99 L, 100 L ou DIVINE. */
    public static TraitRarity rarityFor(int d100, boolean divineOn100) {
        if (d100 <= 40) return TraitRarity.COMMON;
        if (d100 <= 70) return TraitRarity.RARE;
        if (d100 <= 90) return TraitRarity.EPIC;
        if (d100 == 100 && divineOn100) return TraitRarity.DIVINE;
        return TraitRarity.LEGENDARY;
    }

    /** Prodígio: raridade mínima RARE. */
    public static TraitRarity promoteForProdigy(TraitRarity r) {
        return r == TraitRarity.COMMON ? TraitRarity.RARE : r;
    }

    /** Teste de Sangue: lowThreshold (Clássico sem Épico+) -> 15–19 dá 1 clã; senão 18–19; 20 dá 2. */
    public static int clanSlotsFor(int d20, boolean lowThreshold) {
        if (d20 >= 20) return 2;
        if (d20 >= (lowThreshold ? 15 : 18)) return 1;
        return 0;
    }

    /** Cruza as Barreiras de Inaptidão (3→4, 6→7, 9→10). */
    public static boolean crossesBarrier(int before, int after) {
        return (before <= 3 && after >= 4) || (before <= 6 && after >= 7) || (before <= 9 && after >= 10);
    }

    public static Map<Attribute, AttributeLayer> layers(Map<Attribute, Integer> base,
                                                        List<BuildGains> clanGains,
                                                        List<BuildGains> traitGains,
                                                        Map<Attribute, Integer> freePoints) {
        Map<Attribute, AttributeLayer> out = new HashMap<>();
        for (Attribute a : Attribute.values()) {
            int b = get(base, a);
            int clan = sumFlat(clanGains, a);
            int trait = sumFlat(traitGains, a);
            int pct = sumPercent(clanGains, a) + sumPercent(traitGains, a);
            int after = (int) Math.floorDiv((long) (b + clan + trait) * (100 + pct), 100L);
            int free = get(freePoints, a);
            out.put(a, new AttributeLayer(b, clan, trait, pct, after, free, after + free));
        }
        return out;
    }

    /** 6 + Σ freePoints − (canhoto ? 3 : 0); 0 se atributos aleatórios. */
    public static int freePointsAvailable(Dominance d, List<BuildGains> allGains, boolean randomAttributes) {
        if (randomAttributes) return 0;
        return 6 + sum(allGains, BuildGains::getFreePoints) - (d == Dominance.LEFT ? 3 : 0);
    }

    public static ResourceMaxes resources(int finalCon, List<BuildGains> allGains) {
        return new ResourceMaxes(
                SheetRules.initialHpMax(finalCon) + sum(allGains, BuildGains::getMaxHp),
                SheetRules.initialSpMax(finalCon) + sum(allGains, BuildGains::getMaxSp),
                SheetRules.initialKpMax(finalCon) + sum(allGains, BuildGains::getMaxKp));
    }

    /** Soma os ajustes de limitador por chave (nome de Attribute, HP, SP, KP). */
    public static Map<String, Integer> limiterAdjustments(List<BuildGains> allGains) {
        Map<String, Integer> out = new HashMap<>();
        if (allGains == null) return out;
        for (BuildGains g : allGains) {
            if (g == null || g.getLimiters() == null) continue;
            g.getLimiters().forEach((k, v) -> out.merge(k, v == null ? 0 : v, Integer::sum));
        }
        return out;
    }

    private static int get(Map<Attribute, Integer> m, Attribute a) {
        if (m == null) return 0;
        Integer v = m.get(a);
        return v == null ? 0 : v;
    }

    private static int sum(List<BuildGains> gains, Function<BuildGains, Integer> f) {
        if (gains == null) return 0;
        int s = 0;
        for (BuildGains g : gains) {
            if (g != null) s += f.apply(g);
        }
        return s;
    }

    private static int sumFlat(List<BuildGains> gains, Attribute a) {
        return sum(gains, g -> get(g.getAttributeFlat(), a));
    }

    private static int sumPercent(List<BuildGains> gains, Attribute a) {
        return sum(gains, g -> get(g.getAttributePercent(), a));
    }
}
