package com.tatakai.manager.service.rules;

import com.tatakai.manager.entity.Hysteria;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Regras puras de desmaio, Adrenalina e Dissociação (F6c). Sem Spring, sem aleatoriedade. */
public final class VitalRules {

    public enum Tier { NONE, FLOW, APEX }

    public static final int MAX_ADRENALINE = 10;
    public static final int FLOW_AT = 5;
    public static final int DEATH_SAVE_ND = 12;
    public static final int SP_ZERO_ND = 12;
    public static final int EXHAUSTION_START_ND = 14;
    public static final int KP_ZERO_BASE_ND = 14;
    public static final int DISSOCIATION_AT = 12;
    public static final int COLLAPSE_AT = 12;
    private static final Set<String> HEAD_REGIONS = Set.of("queixo", "tempora", "nuca", "figado");
    private static final Set<String> CRIT_DOUBLE_REGIONS = Set.of("queixo", "nuca");

    private VitalRules() {
    }

    public static Tier tier(int adrenaline) {
        return adrenaline >= MAX_ADRENALINE ? Tier.APEX : adrenaline >= FLOW_AT ? Tier.FLOW : Tier.NONE;
    }

    /** Histeria só vale enquanto está no Ápice. */
    public static Hysteria activeHysteria(int adrenaline, Hysteria h) {
        return tier(adrenaline) == Tier.APEX ? h : null;
    }

    public static boolean dissociated(int mentalFatigue) {
        return mentalFatigue >= DISSOCIATION_AT;
    }

    public static boolean collapse(int physicalFatigue) {
        return physicalFatigue >= COLLAPSE_AT;
    }

    /** SP de técnicas/ações: Dissociação ×2, depois Fluxo −1 (mín. 0, só quando há custo). */
    public static int adjustSp(int cost, Tier tier, boolean dissociated) {
        int c = dissociated ? cost * 2 : cost;
        return tier != Tier.NONE && c > 0 ? c - 1 : c;
    }

    public static int critMargin(Tier tier) {
        return tier == Tier.APEX ? 19 : RollRules.CRIT_MARGIN;
    }

    public static int clampAdrenaline(int v) {
        return Math.max(0, Math.min(MAX_ADRENALINE, v));
    }

    /** Crise (a): dano num turno >= metade do HP máximo. */
    public static boolean crisisByDamage(int damageThisTurn, int hpMax) {
        return damageThisTurn > 0 && 2 * damageThisTurn >= hpMax;
    }

    /** Crise (b): HP > 0 e <= 1/4 do máximo. */
    public static boolean crisisByLowHp(int hp, int hpMax) {
        return hp > 0 && 4 * hp <= hpMax;
    }

    /** Golpe único que zera e excede em >= metade do HP máximo mata (LETHAL). */
    public static boolean massiveDamage(int excess, int hpMax) {
        return excess > 0 && 2 * excess >= hpMax;
    }

    public static int kpZeroNd(int zeroings) {
        return KP_ZERO_BASE_ND + 2 * zeroings;
    }

    /** Ataque Localizado acerta a região de um ferimento ("Braço" casa com "braço esquerdo (Membro Inútil)")? */
    public static boolean sameRegion(String attackRegion, String injuryRegion) {
        String a = normalizeRegion(attackRegion);
        String i = normalizeRegion(injuryRegion);
        return !a.isEmpty() && !i.isEmpty() && (i.startsWith(a) || a.startsWith(i.replaceAll("\\s*\\(.*\\)$", "")));
    }

    /** Estabilizar: INT contra ND 12, +3 a cada falha anterior no mesmo lutador. */
    public static int stabilizeNd(int failures) {
        return 12 + 3 * Math.max(0, failures);
    }

    public static String normalizeRegion(String region) {
        if (region == null) return "";
        return Normalizer.normalize(region.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    /** Perda de PQ por golpe que conecta (RN-ATQ-07 simplificada). */
    public static int kpLoss(String region, int headImpact, int power, boolean critical, boolean wall) {
        String r = normalizeRegion(region);
        if (!HEAD_REGIONS.contains(r)) return 0;
        int loss = 1 + Math.max(0, headImpact) + Math.max(0, power);
        if (critical && CRIT_DOUBLE_REGIONS.contains(r)) loss *= 2;
        if (wall) loss -= 1;
        return Math.max(0, loss);
    }
}
