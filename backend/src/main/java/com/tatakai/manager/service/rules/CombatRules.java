package com.tatakai.manager.service.rules;

import com.tatakai.manager.entity.CombatAction;
import com.tatakai.manager.entity.ConditionExpiry;

import java.util.List;
import java.util.Random;
import java.util.function.ToIntFunction;
import java.util.Collections;
import java.util.Comparator;

/** Regras puras do motor de combate (F6a): iniciativa, PA, custos, deslocamento e expiração de condições. */
public final class CombatRules {

    public static final int BASE_PA = 5;
    private static final double MIN_MOVE = 1.5;

    private CombatRules() {
    }

    /** RN-ACAO-04: mod(DES) com arte ativa, senão metade; menos a penalidade de Desgaste Físico. */
    public static int initiativeBonus(int dex, boolean hasActiveArt, int physicalFatiguePoints) {
        Integer m = SheetRules.mod(dex);
        int base = m == null ? 0 : m;
        int bonus = hasActiveArt ? base : SheetRules.half(base);
        return bonus - fatiguePenalty(physicalFatiguePoints);
    }

    public static int fatiguePenalty(int points) {
        return switch (SheetRules.fatigueBand(points)) {
            case RESTED -> 0;
            case TIRED -> 1;
            case DRAINED -> 2;
            case EXHAUSTED, COLLAPSE -> 3;
        };
    }

    public static String initiativeExpression(int bonus) {
        return bonus == 0 ? "1d20" : "1d20" + (bonus > 0 ? "+" : "-") + Math.abs(bonus);
    }

    /** Ordem: maior iniciativa, depois maior DES, depois sorteio (embaralha antes de uma ordenação estável). */
    public static <T> void sortOrder(List<T> list, ToIntFunction<T> initiative, ToIntFunction<T> dex, Random random) {
        Collections.shuffle(list, random);
        list.sort(Comparator.comparingInt(initiative).reversed().thenComparing(Comparator.comparingInt(dex).reversed()));
    }

    /** RN-ACAO-01: 5 + bônus − perda, mínimo 0. */
    public static int rechargePa(int bonusNext, int lossNext) {
        return Math.max(0, BASE_PA + bonusNext - lossNext);
    }

    public static int paCost(CombatAction a, boolean slow) {
        return switch (a) {
            case MAJOR -> slow ? 4 : 3;
            case PREPARED -> 3;
            case BREATHE -> 4;
            case MINOR, SHOVE, CHANGE_GUARD, CHANGE_BASE -> 1;
            case STANCH -> 3;
            case MOVE -> 1;
            case MINOR_OFFENSIVE, DISENGAGE, STAND_UP -> 2;
            case FREE -> 0;
        };
    }

    /** §26.4: entrar numa base (ou sair dela) = 1 PA; trocar de uma base para outra = 2 PA. */
    public static int baseChangeCost(boolean hasBase, boolean toBase) {
        return hasBase && toBase ? 2 : 1;
    }

    /** Ações que gastam SP (padrão 1); as demais ignoram o valor informado. */
    public static int spCost(CombatAction a, Integer requested) {
        return switch (a) {
            case MAJOR, PREPARED, MINOR_OFFENSIVE -> requested == null ? 1 : Math.max(0, requested);
            default -> 0;
        };
    }

    /** Recuperar Fôlego: 2 + modQ(CON). */
    public static int breatheRecovery(int con) {
        return 2 + SheetRules.modQ(con);
    }

    /** RN-ACAO-03: distância do n-ésimo deslocamento (n >= 1) no turno. */
    public static double moveDistance(int n, int dex) {
        double d = Math.max(MIN_MOVE, 4 + SheetRules.modQ(dex));
        for (int i = 2; i <= n; i++) d = Math.max(MIN_MOVE, d / 2);
        return d;
    }

    public static boolean expiresAtTurnStart(ConditionExpiry e) {
        return e == ConditionExpiry.START_OF_TARGET_TURN;
    }

    /** RN-TURNO-01: aplicada durante o próprio turno do alvo (applied == turnStartedAt) só expira no fim do próximo. */
    public static boolean expiresAtTurnEnd(ConditionExpiry e, int appliedTurn, int turnStartedAt) {
        return e == ConditionExpiry.END_OF_TARGET_TURN && appliedTurn < turnStartedAt;
    }

    public static boolean expiresAtRoundEnd(ConditionExpiry e) {
        return e == ConditionExpiry.END_OF_ROUND;
    }

    /** Contador ao fim do turno do alvo: devolve o que resta (expira em <= 0). */
    public static int tickCounter(Integer turnsRemaining) {
        return (turnsRemaining == null ? 0 : turnsRemaining) - 1;
    }
}
