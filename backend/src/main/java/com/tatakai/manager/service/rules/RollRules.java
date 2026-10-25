package com.tatakai.manager.service.rules;

import com.tatakai.manager.entity.FatigueBand;

import java.util.List;

/** Regras puras de rolagem (F5, PLANO §20.1). Sem Spring, sem aleatoriedade. */
public final class RollRules {

    private RollRules() {
    }

    /** Dados de dano: termo principal (`count`d`sides`) e termos extras somados (ex.: 1d12+1d4, 2d10+1d4). */
    public record Dice(int count, int sides, List<Dice> plus) {
        public Dice(int count, int sides) {
            this(count, sides, List.of());
        }

        public Dice {
            plus = plus == null ? List.of() : List.copyOf(plus);
        }

        /** Todos os termos simples, o principal primeiro. */
        public List<Dice> terms() {
            List<Dice> out = new java.util.ArrayList<>();
            out.add(new Dice(count, sides));
            out.addAll(plus);
            return out;
        }

        /** Escala de Dano: multiplica a quantidade de cada termo. */
        public Dice scaled(int factor) {
            return new Dice(count * factor, sides, plus.stream().map(d -> d.scaled(factor)).toList());
        }

        /** Soma outro termo (ex.: Dado Adicional de técnica armada). */
        public Dice with(Dice extra) {
            List<Dice> l = new java.util.ArrayList<>(plus);
            l.addAll(extra.terms());
            return new Dice(count, sides, l);
        }

        public String text() {
            StringBuilder b = new StringBuilder(count + "d" + sides);
            for (Dice d : plus) b.append('+').append(d.count()).append('d').append(d.sides());
            return b.toString();
        }

        /** "2d10 + 1d4" → Dice; null/vazio → null. */
        public static Dice parse(String text) {
            if (text == null || text.isBlank()) return null;
            List<Dice> terms = new java.util.ArrayList<>();
            for (String t : text.replace(" ", "").toLowerCase().split("\\+")) {
                String[] p = t.split("d");
                terms.add(new Dice(Integer.parseInt(p[0]), Integer.parseInt(p[1])));
            }
            return new Dice(terms.get(0).count(), terms.get(0).sides(), terms.subList(1, terms.size()));
        }
    }

    /** Degraus de Dado da Criação Marcial: 1d4 … 1d12, 1d12+1d4, 1d12+1d6, 1d12+1d8, 2d12. */
    private static final Dice[] SCALE = {
            new Dice(1, 4), new Dice(1, 6), new Dice(1, 8), new Dice(1, 10), new Dice(1, 12),
            new Dice(1, 12, List.of(new Dice(1, 4))), new Dice(1, 12, List.of(new Dice(1, 6))),
            new Dice(1, 12, List.of(new Dice(1, 8))), new Dice(2, 12)};

    public static final int CRIT_MARGIN = 20;

    public static int clampAdvantage(int n) {
        return Math.max(-3, Math.min(3, n));
    }

    /** Quantidade de d20 rolados: 2 com vantagem/desvantagem líquida, senão 1. */
    public static int d20Count(int net) {
        return net == 0 ? 1 : 2;
    }

    /** Maior de 2d20 com vantagem, menor com desvantagem, único d20 sem. */
    public static int pickNatural(int net, List<Integer> d20s) {
        if (net > 0) return Math.max(d20s.get(0), d20s.get(1));
        if (net < 0) return Math.min(d20s.get(0), d20s.get(1));
        return d20s.get(0);
    }

    /** Bônus somado ao total: +min(n−1,2) com vantagem, −min(|n|−1,2) com desvantagem. */
    public static int advantageBonus(int net) {
        if (net >= 1) return Math.min(net - 1, 2);
        if (net <= -1) return -Math.min(-net - 1, 2);
        return 0;
    }

    public static boolean critical(int natural, int critMargin) {
        return natural >= critMargin && !fumble(natural); // 1 natural é sempre falha, mesmo com Crítico Garantido
    }

    public static boolean fumble(int natural) {
        return natural == 1;
    }

    /** Escala de dado da técnica pelo degrau (qtd. de "Dado Maior"), limitado a 0..8. */
    public static Dice diceScale(int degree) {
        return SCALE[Math.max(0, Math.min(8, degree))];
    }

    /** Penalidade de acerto/teste pelo Desgaste da trilha: Cansado −1, Esgotado −2, Exaurido/Colapso −3. */
    public static int fatiguePenalty(FatigueBand band) {
        return switch (band) {
            case RESTED -> 0;
            case TIRED -> -1;
            case DRAINED -> -2;
            case EXHAUSTED, COLLAPSE -> -3;
        };
    }

    /** Gasto de SP exibido: base − 2 por COST (mín. 0) + 2/4/6 do Desgaste Físico. */
    public static int spCost(Integer base, int costBonuses, FatigueBand physical) {
        int c = Math.max(0, (base == null ? 0 : base) - 2 * costBonuses);
        return c - 2 * fatiguePenalty(physical);
    }

    /** E do dano: floor(FOR/30) + 1 se a técnica tem APEX. */
    public static int strengthExtra(int str, boolean apex) {
        return Math.max(0, str) / 30 + (apex ? 1 : 0);
    }

    /** Modificador de atributo tratando atributo 0 (mod nulo) como 0. */
    public static int modOrZero(int attr) {
        Integer m = SheetRules.mod(attr);
        return m == null ? 0 : m;
    }

    /** Precisão de base em Esboço vale metade (floor). */
    public static int sketchHalf(int qty, boolean sketch) {
        return sketch ? qty / 2 : qty;
    }

    public static String signed(int v) {
        return v >= 0 ? "+" + v : String.valueOf(v);
    }
}
