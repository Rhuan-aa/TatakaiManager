package com.tatakai.manager.service.rules;

import com.tatakai.manager.entity.AssemblyItem;
import com.tatakai.manager.entity.MartialType;
import com.tatakai.manager.entity.ReactionType;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Números da base ativa (§31). O valor e o escopo vêm do **rótulo** do item ("Guarda +3 em Defender",
 * "Penalidade −2 em Esquiva", "Precisão +1 em Derrubada", "Calejamento 3 contundente"): a quantidade guarda o custo
 * nos itens não afins, então não serve como valor. Puro.
 */
public final class BaseRules {

    /** A que o item se aplica: todos, uma reação, um tipo marcial de ataque, ou algo não modelado (ignorado). */
    public enum Scope { ALL, DEFEND, DODGE, COUNTER, STRIKING, TAKEDOWN, SWEEP, OTHER }

    public record Item(String module, Scope scope, double value) {}

    /** Base parseada; em Esboço os bônus já vêm pela metade (arredondada para baixo) e as reduções inteiras. */
    public record Profile(String name, List<Item> items, boolean sketch) {

        private int sum(String module, Scope... scopes) {
            double v = 0;
            for (Item i : items) {
                if (!i.module().equals(module)) continue;
                for (Scope s : scopes) if (i.scope() == s) v += i.value();
            }
            return (int) v;
        }

        /** Precisão no acerto do ataque do tipo `t` (genérica + a do tipo). */
        public int precision(MartialType t) {
            return sum("Precisão", typed(t));
        }

        /** Força do Golpe no dano do ataque do tipo `t`. */
        public int strike(MartialType t) {
            return sum("Força do Golpe", typed(t));
        }

        /** Penalidade no acerto de ataques do tipo `t` (ex.: "Penalidade −2 em Trocação"); ≤ 0. */
        public int attackPenalty(MartialType t) {
            return t == null || scopeOf(t) == Scope.OTHER ? 0 : sum("Penalidade", scopeOf(t));
        }

        /** Guarda (+) e Penalidade (−) na reação `r`. */
        public int reaction(ReactionType r) {
            Scope s = switch (r) {
                case DEFEND -> Scope.DEFEND;
                case DODGE -> Scope.DODGE;
                case COUNTER -> Scope.COUNTER;
                case NONE -> null;
            };
            return s == null ? 0 : sum("Guarda", s) + sum("Penalidade", s);
        }

        /** Calejamento: Redução de Dano (valor do rótulo; Esboço metade). */
        public int calejo() {
            return sum("Calejamento", Scope.ALL);
        }

        /** Enraizado: bônus para resistir a Empurrar, Derrubar e Quebrar Base. */
        public int rooted() {
            return sum("Enraizado", Scope.ALL);
        }

        /** Resiliência: bônus em todos os testes de resistência contra Efeitos. */
        public int resilience() {
            return sum("Resiliência", Scope.ALL);
        }

        /** Calma: bônus nos Testes de Vontade. */
        public int calm() {
            return sum("Calma", Scope.ALL);
        }

        /** Passada (+) e Pesada (−), em metros por Movimentação. */
        public double moveMeters() {
            double v = 0;
            for (Item i : items) {
                if (i.module().equals("Passada") || i.module().equals("Pesada")) v += i.value();
            }
            return v;
        }

        /** Fraqueza contra o tipo do atacante: desvantagem nas reações. */
        public boolean weakAgainst(MartialType attacker) {
            return has("Fraqueza", attacker);
        }

        /** Leitura de Estilo contra o tipo do atacante: vantagem nas reações. */
        public boolean readsAgainst(MartialType attacker) {
            return has("Leitura de Estilo", attacker);
        }

        /** Genérico + o escopo do tipo (tipo desconhecido/Geral: só o genérico). */
        private static Scope[] typed(MartialType t) {
            Scope s = scopeOf(t);
            return s == Scope.OTHER ? new Scope[] {Scope.ALL} : new Scope[] {Scope.ALL, s};
        }

        private boolean has(String module, MartialType t) {
            Scope s = scopeOf(t);
            return s != Scope.OTHER && items.stream().anyMatch(i -> i.module().equals(module) && i.scope() == s);
        }
    }

    private static final Pattern SIGNED = Pattern.compile("([+−-])\\s*(\\d+(?:,\\d+)?)");
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:,\\d+)?)");

    private BaseRules() {}

    public static Profile profile(String name, List<AssemblyItem> items, boolean sketch) {
        return new Profile(name, items == null ? List.of() : items.stream()
                .filter(i -> i.getModule() != null)
                .map(i -> new Item(i.getModule(), scope(i.getLabel()), sketchValue(valueOrQty(i), sketch)))
                .toList(), sketch);
    }

    /** Esboço: "concede apenas metade dos seus bônus (arredondada para baixo), mas aplica as reduções por inteiro". */
    private static double sketchValue(double v, boolean sketch) {
        return sketch && v > 0 ? Math.floor(v / 2) : v;
    }

    /** Valor do rótulo; sem número no rótulo, a quantidade (itens antigos sem rótulo). */
    private static double valueOrQty(AssemblyItem i) {
        return i.getLabel() != null && NUMBER.matcher(i.getLabel()).find() ? value(i.getLabel()) : i.getQty();
    }

    /** Primeiro número com sinal do rótulo ("−2" → −2, "+1,5 m" → 1.5); sem sinal, o primeiro número; nada → 0. */
    public static double value(String label) {
        if (label == null) return 0;
        Matcher m = SIGNED.matcher(label);
        if (m.find()) {
            double v = Double.parseDouble(m.group(2).replace(',', '.'));
            return m.group(1).equals("+") ? v : -v;
        }
        m = NUMBER.matcher(label);
        return m.find() ? Double.parseDouble(m.group(1).replace(',', '.')) : 0;
    }

    public static Scope scope(String label) {
        if (label == null) return Scope.ALL;
        String l = label.toLowerCase(Locale.ROOT);
        if (l.contains("fora de")) return Scope.OTHER;
        if (l.contains("contra-ataque")) return Scope.COUNTER;
        if (l.contains("defender")) return Scope.DEFEND;
        if (l.contains("esquiva")) return Scope.DODGE;
        if (l.contains("trocação")) return Scope.STRIKING;
        if (l.contains("derrubada")) return Scope.TAKEDOWN;
        if (l.contains("raspagem")) return Scope.SWEEP;
        return Scope.ALL;
    }

    public static Scope scopeOf(MartialType t) {
        if (t == null) return Scope.OTHER;
        return switch (t) {
            case STRIKING -> Scope.STRIKING;
            case TAKEDOWN -> Scope.TAKEDOWN;
            case SWEEP -> Scope.SWEEP;
            case GENERAL -> Scope.OTHER;
        };
    }
}
