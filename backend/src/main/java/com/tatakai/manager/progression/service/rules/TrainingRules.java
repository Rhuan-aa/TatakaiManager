package com.tatakai.manager.progression.service.rules;

import com.tatakai.manager.progression.entity.RestQuality;
import com.tatakai.manager.progression.entity.TrainingVenue;
import com.tatakai.manager.sheet.entity.BodyType;
import com.tatakai.manager.sheet.entity.FatigueBand;
import com.tatakai.manager.sheet.service.rules.SheetRules;

import java.util.List;
import java.util.Set;

/** Regras puras de treino e Repouso fora de combate (spec §9.2, RN-PROG-01..06, RN-ATR-06). */
public final class TrainingRules {

    public static final String TECHNIQUE = "TECHNIQUE";
    public static final String GRADUATION = "GRADUATION";
    public static final List<String> TARGETS =
            List.of("STR", "DEX", "CON", "INT", "CHA", "MEN", "HP", "SP", "KP", TECHNIQUE, GRADUATION);
    private static final Set<String> MENTAL = Set.of("INT", "CHA", "MEN");

    private TrainingRules() {
    }

    /** Resultado de um treino: ganho (pendente), se foi Pleno, Desgaste causado e se foi bloqueado. */
    public record Outcome(int gain, boolean full, int fatigue, boolean lost, String note) {}

    public static boolean isMental(String target) {
        return MENTAL.contains(target);
    }

    /** RN-PROG-01. PQ: 1 a cada 4º treino de PQ (`kpIndex` é a contagem 1-based deste treino), sem TAL. */
    public static int baseGain(String target, int tal, BodyType bt, int kpIndex) {
        if ("KP".equals(target)) return kpIndex % 4 == 0 ? 1 : 0;
        int base = switch (target) {
            case "CHA", "HP", "SP" -> 2;
            default -> 1;
        };
        return base + SheetRules.modQ(tal) / 2 + bodyBonus(target, bt);
    }

    /** +1 de treino do tipo corporal: Endomorfo CON, Mesomorfo FOR, Ectomorfo DES. */
    static int bodyBonus(String target, BodyType bt) {
        if (bt == null) return 0;
        return switch (bt) {
            case ENDOMORPH -> "CON".equals(target) ? 1 : 0;
            case MESOMORPH -> "STR".equals(target) ? 1 : 0;
            case ECTOMORPH -> "DEX".equals(target) ? 1 : 0;
        };
    }

    /** Treinos Plenos por semana e alvo físico (RN-PROG-03). ponytail: Platô e Lotada ficam para depois. */
    public static int weeklyFullLimit(TrainingVenue v) {
        if (v == null) return 3;
        return switch (v) {
            case FIELD -> 2;
            case COMMON_GYM, FIGHT_GYM -> 3;
            case HIGH_PERFORMANCE -> 4;
        };
    }

    /**
     * Um treino de atributo/recurso. `nthToday`: 1-based no dia e alvo (Sobrecarga); `fullsThisWeek`: Plenos já
     * feitos na semana neste alvo; `band`: faixa de Desgaste do tipo antes do treino.
     */
    public static Outcome train(String target, int tal, BodyType bt, int nthToday, int fullsThisWeek, int kpIndex,
                                FatigueBand band, TrainingVenue venue) {
        return train(target, tal, bt, nthToday, fullsThisWeek, kpIndex, band, venue, false);
    }

    /**
     * Um treino de atributo/recurso. `nthToday`: 1-based no dia e alvo (Sobrecarga); `fullsThisWeek`: Plenos já
     * feitos na semana neste alvo; `band`: faixa de Desgaste do tipo antes do treino; `portableKit`: se usa Kit de Treino Portátil.
     */
    public static Outcome train(String target, int tal, BodyType bt, int nthToday, int fullsThisWeek, int kpIndex,
                                FatigueBand band, TrainingVenue venue, boolean portableKit) {
        if (band == FatigueBand.COLLAPSE) return new Outcome(0, false, 0, true, "Colapso: treino bloqueado");
        boolean mental = isMental(target);
        int fatigue = nthToday == 2 ? 2 : 1;
        if (nthToday >= 3) return new Outcome(0, false, fatigue, false, "Sobrecarga: 3º treino do dia no alvo");

        int gain = baseGain(target, tal, bt, kpIndex);
        boolean full = true;
        if (!mental) {
            int baseLimit = weeklyFullLimit(venue);
            if (venue == TrainingVenue.FIELD && portableKit && ("STR".equals(target) || "CON".equals(target) || "DEX".equals(target))) {
                baseLimit += 1;
            }
            int limit = baseLimit - (band == FatigueBand.DRAINED ? 1 : 0);
            full = fullsThisWeek < Math.max(1, limit);
        } else if (band == FatigueBand.DRAINED) {
            gain = Math.max(0, gain - 1);
        }
        if (band == FatigueBand.EXHAUSTED) full = false;
        if (!full) gain = "KP".equals(target) ? 0 : gain / 2;
        return new Outcome(gain, full, fatigue, false, full ? null : "Treino Parcial");
    }

    /** RN-ATR-06: ganho por treino que cruzaria 3→4, 6→7 ou 9→10 para no topo da faixa. */
    public static int barrierCap(int before, int gain) {
        int after = before + gain;
        for (int top : new int[]{3, 6, 9}) {
            if (before <= top && after > top) return top - before;
        }
        return gain;
    }

    public static int restDie(RestQuality q) {
        return switch (q) {
            case PRECARIOUS -> 6;
            case SIMPLE -> 8;
            case COMFORTABLE -> 10;
            case TREATED -> 12;
        };
    }

    /** Desgaste recuperado por Repouso (por tipo). */
    public static int restFatigueRecovery(RestQuality q) {
        return switch (q) {
            case PRECARIOUS -> 0;
            case SIMPLE, COMFORTABLE -> 1;
            case TREATED -> 2;
        };
    }

    /** RN-PROG-04: Repouso Precário reduz 1 por treino (mín. 1). */
    public static int precarious(int gain) {
        return gain <= 0 ? gain : Math.max(1, gain - 1);
    }

    /**
     * Dias de 1..totalDays que dariam +1 Desgaste Físico por falta de Repouso (§51.2): sem REPOUSO e com treino/forja
     * ou o dia cheio (slots 1..slotsPerDay ocupados). Espelha {@code TrainingService.processDay}.
     */
    public static int unrestedDays(List<com.tatakai.manager.timeskip.entity.Booking> mine, int totalDays, int slotsPerDay) {
        int count = 0;
        for (int day = 1; day <= totalDays; day++) {
            boolean rested = false, works = false;
            int used = 0;
            for (var b : mine) {
                if (b.getTimeSkipDay().getDayNumber() != day) continue;
                var solo = b.getNpcId() == null ? b.getSoloActivityType() : null;
                if (solo == com.tatakai.manager.timeskip.entity.SoloActivityType.REPOUSO && b.getRestQuality() != null) rested = true;
                if (solo == com.tatakai.manager.timeskip.entity.SoloActivityType.TREINO && b.getTrainingTarget() != null
                        || solo == com.tatakai.manager.timeskip.entity.SoloActivityType.FORJA) works = true;
                if (b.getSlotNumber() <= 4) used += Math.max(1, (int) b.getIdlePointCost());
            }
            if (!rested && (works || used >= slotsPerDay)) count++;
        }
        return count;
    }
}
