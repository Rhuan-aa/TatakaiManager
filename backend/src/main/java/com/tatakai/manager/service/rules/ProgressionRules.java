package com.tatakai.manager.service.rules;

import com.tatakai.manager.entity.FightClassification;
import com.tatakai.manager.entity.Graduation;
import com.tatakai.manager.entity.KeikenRank;
import com.tatakai.manager.entity.Mastery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Regras puras de EL, PS, Keiken e graduação (spec §9.2, RN-PROG-07..09; PLANO §18). */
public final class ProgressionRules {

    public static final int MAX_MILESTONES = 4;
    public static final int MAX_SOCIAL_PS = 2;
    public static final int STATUS_PER_PS = 3;
    public static final Set<String> ATTRIBUTE_TARGETS = Set.of("STR", "DEX", "CON", "INT", "CHA", "MEN");
    public static final Set<String> STATUS_TARGETS = Set.of("HP", "SP");
    /** Teto acumulado de EL de sparring por patente (não reseta ao subir). */
    private static final int[] SPARRING_CAPS = {3, 5, 10, 14, 18, 23, 30};
    private static final KeikenRank[] MASTER_KEIKEN =
            {KeikenRank.VETERAN, KeikenRank.HISTORIC, KeikenRank.LEGEND, KeikenRank.MYTHIC, KeikenRank.DRAGON_GOD};

    private ProgressionRules() {
    }

    public record Requirement(String key, boolean met, String detail) {}

    public static KeikenRank keiken(int el) {
        KeikenRank r = KeikenRank.NOVICE;
        for (KeikenRank k : KeikenRank.values()) if (el >= k.minEl) r = k;
        return r;
    }

    /** EL que leva à próxima patente, ou null em Deus-Dragão. */
    public static Integer nextKeikenAt(int el) {
        int next = keiken(el).ordinal() + 1;
        return next < KeikenRank.values().length ? KeikenRank.values()[next].minEl : null;
    }

    public static int sparringCap(int el) {
        return SPARRING_CAPS[keiken(el).ordinal()];
    }

    /** Uma vaga de Instinto por patamar acima de Novato. */
    public static int instinctSlots(int el) {
        return keiken(el).ordinal();
    }

    /** RN-PROG-07: EL da luta. Fácil só 1×/dia; sparring até o teto acumulado da patente. */
    public static int fightEl(FightClassification c, boolean easyAlreadyToday, int sparringEl, int el) {
        return switch (c) {
            case EASY -> easyAlreadyToday ? 0 : 1;
            case REAL -> 2;
            case LETHAL -> 3;
            case SPARRING -> sparringEl < sparringCap(el) ? 1 : 0;
        };
    }

    /** PS = dificuldade (1/2/3) + min(Marcos, 4) + floor(modQ(TAL)/2); sparring não dá PS. */
    public static int fightPs(FightClassification c, int milestones, int tal) {
        if (c == FightClassification.SPARRING) return 0;
        return c.ordinal() + 1 + Math.min(milestones, MAX_MILESTONES) + SheetRules.modQ(tal) / 2;
    }

    public static boolean crossesBarrier(FightClassification c) {
        return c == FightClassification.REAL || c == FightClassification.LETHAL;
    }

    /** Validação estática de uma distribuição de PS; devolve a mensagem de erro ou null. */
    public static String validateAllocation(Map<String, Integer> alloc, int pending) {
        if (alloc == null || alloc.isEmpty()) return "Distribua ao menos 1 PS";
        int sum = 0;
        for (var e : alloc.entrySet()) {
            String t = e.getKey();
            int n = e.getValue() == null ? 0 : e.getValue();
            if (!ATTRIBUTE_TARGETS.contains(t) && !STATUS_TARGETS.contains(t)) return "Alvo inválido para PS: " + t;
            if (n <= 0) return "Quantidade de PS inválida em " + t;
            if (("INT".equals(t) || "CHA".equals(t)) && n > MAX_SOCIAL_PS) return t + " recebe no máximo 2 PS";
            sum += n;
        }
        return sum > pending ? "PS insuficientes (" + pending + " pendentes)" : null;
    }

    /** §9.3: Keiken mínima para forjar na graduação (Genérica nenhuma). */
    public static KeikenRank forgeKeiken(Graduation g) {
        return switch (g == null ? Graduation.GENERIC : g) {
            case GENERIC -> KeikenRank.NOVICE;
            case BEGINNER -> KeikenRank.SEASONED;
            case INTERMEDIATE -> KeikenRank.VETERAN;
            case MASTER -> KeikenRank.HISTORIC;
        };
    }

    public static Graduation nextGraduation(Graduation g) {
        return switch (g) {
            case BEGINNER -> Graduation.INTERMEDIATE;
            case INTERMEDIATE -> Graduation.MASTER;
            default -> null;
        };
    }

    /** §7.7: 8 (Intermediário) / 12 (Mestre); arte Geral 9 / 18. */
    public static int graduationCost(Graduation next, boolean general) {
        return next == Graduation.MASTER ? (general ? 18 : 12) : (general ? 9 : 8);
    }

    public static KeikenRank requiredKeiken(Graduation next, int mastersAlready) {
        return next == Graduation.MASTER ? MASTER_KEIKEN[Math.min(mastersAlready, MASTER_KEIKEN.length - 1)]
                : KeikenRank.SEASONED;
    }

    /**
     * RN-PROG-09. `learned` = maestrias das técnicas aprendidas da graduação atual na arte; `mastersAlready` = artes
     * da ficha já em Mestre. O duelo (só para Mestre) é confirmado pelo Mestre da mesa.
     */
    public static List<Requirement> graduationRequirements(Graduation next, List<Mastery> learned, int el,
                                                           int mastersAlready, int points, boolean general,
                                                           boolean duelWon) {
        List<Requirement> out = new ArrayList<>();
        out.add(new Requirement("COMPLETENESS", learned.size() >= 3, Math.min(learned.size(), 3) + "/3 técnicas"));
        long improved = learned.stream().filter(m -> m.compareTo(Mastery.IMPROVED) >= 0).count();
        long specialized = learned.stream().filter(m -> m.compareTo(Mastery.SPECIALIZED) >= 0).count();
        out.add(new Requirement("SPECIALIZATION", improved >= 3 && specialized >= 1,
                "2 Melhoradas e 1 Especializada (" + improved + " Melhoradas+, " + specialized + " Especializadas+)"));
        KeikenRank need = requiredKeiken(next, mastersAlready);
        out.add(new Requirement("KEIKEN", keiken(el).compareTo(need) >= 0, need.name()));
        int cost = graduationCost(next, general);
        out.add(new Requirement("POINTS", points >= cost, points + "/" + cost));
        if (next == Graduation.MASTER) out.add(new Requirement("DUEL", duelWon, "Vencer o duelo pelo Critério de Vitória"));
        return out;
    }
}
