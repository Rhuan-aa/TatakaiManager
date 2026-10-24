package com.tatakai.manager.service.rules;

import com.tatakai.manager.entity.Mastery;
import com.tatakai.manager.entity.MasteryBonus;
import com.tatakai.manager.entity.MoveKind;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Regras puras de maestria e treino (spec §7.6, §7.7). */
public final class MasteryRules {

    public static final int MAX_ACTIVE_PER_GRADUATION = 3;
    public static final int MAX_STORED_PER_GRADUATION = 2;
    public static final int MAX_JOKER = 2;

    private static final Set<MasteryBonus> TECHNIQUE_BONUSES = EnumSet.of(MasteryBonus.DAMAGE, MasteryBonus.HIT,
            MasteryBonus.COST, MasteryBonus.IMPACT, MasteryBonus.POWER, MasteryBonus.JOKER, MasteryBonus.REFINE,
            MasteryBonus.TRACTION);
    private static final Set<MasteryBonus> BASE_BONUSES = EnumSet.of(MasteryBonus.BONUS, MasteryBonus.JOKER,
            MasteryBonus.REFINE, MasteryBonus.TRACTION, MasteryBonus.REDUCTION);

    private MasteryRules() {
    }

    /** Próximo nível alcançável por pontos (a partir de Padrão); null se não sobe por pontos. */
    public static Mastery nextLevel(Mastery m) {
        return switch (m) {
            case STANDARD -> Mastery.IMPROVED;
            case IMPROVED -> Mastery.SPECIALIZED;
            case SPECIALIZED -> Mastery.MASTERED;
            default -> null;
        };
    }

    /** RN-MAE-01: pontos de maestria para chegar em `target`. */
    public static int levelCost(int pt, Mastery target) {
        return switch (target) {
            case IMPROVED -> pt;
            case SPECIALIZED -> pt + pt / 2;
            case MASTERED -> 2 * pt;
            default -> 0;
        };
    }

    /** Custo do próximo nível ou null. */
    public static Integer nextLevelCost(int pt, Mastery m) {
        Mastery next = nextLevel(m);
        return next == null ? null : levelCost(pt, next);
    }

    /** Bonificações que cabem no degrau: uma por nível acima de Padrão (as primeiras escolhidas) + Ápice em Masterizada. */
    public static List<MasteryBonus> bonusesFor(Mastery m, List<MasteryBonus> chosen) {
        int levels = Math.max(0, m.ordinal() - Mastery.STANDARD.ordinal());
        List<MasteryBonus> out = new java.util.ArrayList<>(chosen.stream()
                .filter(b -> b != MasteryBonus.APEX).limit(levels).toList());
        if (m == Mastery.MASTERED) out.add(MasteryBonus.APEX);
        return out;
    }

    /** RN-MAE-02 / §7.7: ganho por treino = 1 + floor(modQ(TAL)/2). */
    public static int trainingGain(int tal) {
        return 1 + SheetRules.modQ(tal) / 2;
    }

    /** RN-MAE-03: bonificação válida para o tipo, com Coringa limitado a 2; APEX não é escolhível. */
    public static boolean bonusAllowed(MoveKind kind, MasteryBonus bonus, List<MasteryBonus> current) {
        if (!(kind == MoveKind.BASE ? BASE_BONUSES : TECHNIQUE_BONUSES).contains(bonus)) return false;
        return bonus != MasteryBonus.JOKER || current.stream().filter(b -> b == MasteryBonus.JOKER).count() < MAX_JOKER;
    }
}
