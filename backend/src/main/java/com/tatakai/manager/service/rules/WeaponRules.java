package com.tatakai.manager.service.rules;

import java.util.List;

/** Contas das armas (capítulo Armas e Criações Armadas, plano §42). Puro. */
public final class WeaponRules {

    /** Dado Adicional da técnica armada por degrau de Dado Maior: +1d2, +1d4, +1d6, +1d8, +1d10, +1d12. */
    private static final int[] EXTRA_SIDES = {2, 4, 6, 8, 10, 12};
    /** Degraus do Golpe Pesado: d4 → d6 → d8 → d10 → d12. */
    private static final int[] STEPS = {4, 6, 8, 10, 12};
    /** Alcance Curto/Mínimo (sem grade): até 1,5 m. */
    public static final double CLOSE_M = 1.5;
    /** Arremesso improvisado de arma corpo a corpo: até 6 m. */
    public static final double IMPROVISED_THROW_M = 6;
    /** Explosivas: acerto contra ND 14. */
    public static final int AREA_ND = 14;
    /** Enredar: soltar com Ação Menor exige FOR ou DES contra ND 14. */
    public static final int ENTANGLE_ND = 14;

    private WeaponRules() {}

    /** Dado Adicional do degrau (1..6); null no degrau 0. */
    public static RollRules.Dice extraDie(int degree) {
        if (degree <= 0) return null;
        return new RollRules.Dice(1, EXTRA_SIDES[Math.min(EXTRA_SIDES.length, degree) - 1]);
    }

    /** Golpe Pesado: cada termo da arma sobe `steps` degraus (máx. 2; d12 e d20 não sobem). */
    public static RollRules.Dice heavier(RollRules.Dice d, int steps) {
        int s = Math.max(0, Math.min(2, steps));
        if (s == 0 || d == null) return d;
        List<RollRules.Dice> terms = d.terms().stream().map(t -> new RollRules.Dice(t.count(), step(t.sides(), s))).toList();
        return new RollRules.Dice(terms.get(0).count(), terms.get(0).sides(), terms.subList(1, terms.size()));
    }

    private static int step(int sides, int steps) {
        for (int i = 0; i < STEPS.length; i++) {
            if (STEPS[i] == sides) return STEPS[Math.min(STEPS.length - 1, i + steps)];
        }
        return sides;
    }

    /** Dispersão: perde 1 dado a cada 10 m de distância (mínimo 1 dado no termo principal). */
    public static RollRules.Dice dispersed(RollRules.Dice d, Double distanceM) {
        if (d == null || distanceM == null || distanceM <= CLOSE_M) return d;
        int lose = (int) Math.floor(distanceM / 10);
        return new RollRules.Dice(Math.max(1, d.count() - lose), d.sides(), d.plus());
    }

    /**
     * Vantagem pela distância de um disparo/arremesso: −1 de X a 2X, −1 colado (≤ 1,5 m, exceto Dispersão); null =
     * fora de alcance (acima de 2X).
     */
    public static Integer rangeAdvantage(double rangeM, Double distanceM, boolean dispersion) {
        if (distanceM == null) return 0;
        if (distanceM > 2 * rangeM) return null;
        int adv = distanceM > rangeM ? -1 : 0;
        if (distanceM <= CLOSE_M && !dispersion) adv--;
        return adv;
    }

    /** Rajada (N): 1 projétil + 1 a cada 2 pontos acima da reação, até N; crítico acerta todos. */
    public static int burstHits(int burst, int attack, int reaction, boolean critical) {
        if (critical) return burst;
        return Math.max(1, Math.min(burst, 1 + Math.max(0, attack - reaction) / 2));
    }

    /** Arma Instável (N) trava com natural ≤ N; toda arma de fogo trava com 1 natural. */
    public static boolean jams(int natural, Integer unstable) {
        return natural == 1 || (unstable != null && natural <= unstable);
    }
}
