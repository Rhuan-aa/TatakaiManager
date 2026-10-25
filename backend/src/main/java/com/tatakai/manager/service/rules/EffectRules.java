package com.tatakai.manager.service.rules;

import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.ConditionExpiry;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Efeitos de módulo aplicados no acerto (tabela de Efeitos da Criação Marcial, plano §30). Puro.
 * Resistência: o alvo rola o atributo indicado contra o **resultado do teste** da técnica (com **Pressão Fixa**, contra
 * ND 10 + mod. de FOR do atacante). "No dado": só com d20 natural ≥ 17. Crítico só dispensa o teste com
 * **Crítico Implacável**.
 */
public final class EffectRules {

    public static final int ON_DIE_MIN = 17;
    public static final int PRESSURE_BASE_ND = 10;
    /** Roubar Tempo tira no máximo 2 PA. */
    public static final int MAX_STOLEN_PA = 2;

    /** Efeitos contra os quais o Enraizado da base ajuda a resistir. */
    public static final Set<String> ROOTED = Set.of("Empurrar", "Derrubar", "Quebrar Base");

    public enum Kind { CONDITION, TAKEDOWN, PUSH, STEAL_PA, WOUND, DEEPEN_WOUND }

    /**
     * condition: chave da condição (null quando o efeito não deixa condição); resist/alt: atributos de resistência
     * (o alvo usa o melhor dos dois); expiry null = padrão do catálogo.
     */
    public record Effect(Kind kind, String condition, Attribute resist, Attribute alt, ConditionExpiry expiry) {
        static Effect cond(String key, Attribute a, ConditionExpiry ex) { return new Effect(Kind.CONDITION, key, a, null, ex); }
    }

    private static final ConditionExpiry NEXT_TURN = ConditionExpiry.END_OF_TARGET_TURN;

    private static final Map<String, Effect> EFFECTS = Map.ofEntries(
            Map.entry("Derrubar", new Effect(Kind.TAKEDOWN, "DERRUBADO", Attribute.STR, Attribute.DEX, null)),
            Map.entry("Quebrar Base", Effect.cond("QUEBRA_DE_BASE", Attribute.DEX, null)),
            Map.entry("Empurrar", new Effect(Kind.PUSH, null, Attribute.STR, null, null)),
            Map.entry("Prender", Effect.cond("AGARRADO", Attribute.STR, null)), // Controle: Imobilizado
            Map.entry("Desorientar", Effect.cond("DESORIENTADO", Attribute.CON, NEXT_TURN)),
            Map.entry("Expor", Effect.cond("VULNERAVEL", Attribute.MEN, NEXT_TURN)),
            Map.entry("Roubar Tempo", new Effect(Kind.STEAL_PA, null, Attribute.MEN, null, null)),
            Map.entry("Sufocar", Effect.cond("SUFOCANDO", Attribute.CON, null)),
            Map.entry("Cegar", Effect.cond("MEIO_CEGO", Attribute.CON, ConditionExpiry.END_OF_ROUND)),
            Map.entry("Aleijar", Effect.cond("ALEIJADO", Attribute.DEX, NEXT_TURN)),
            Map.entry("Enfraquecer", Effect.cond("ENFRAQUECIDO", Attribute.CON, NEXT_TURN)),
            Map.entry("Marcar", Effect.cond("MARCADO", Attribute.MEN, ConditionExpiry.END_OF_ROUND)),
            Map.entry("Abrir Ferida", new Effect(Kind.WOUND, null, Attribute.CON, null, null)),
            Map.entry("Aprofundar Ferida", new Effect(Kind.DEEPEN_WOUND, null, Attribute.CON, null, null)),
            Map.entry("Atordoar", Effect.cond("ATORDOADO", Attribute.CON, null)),
            Map.entry("Paralisar", Effect.cond("PARALISADO", Attribute.CON, NEXT_TURN)));

    private EffectRules() {}

    /** Efeito automático do módulo; vazio = o Mestre aplica à mão (Interromper, Esmagar). */
    public static Optional<Effect> of(String module) {
        return Optional.ofNullable(module == null ? null : EFFECTS.get(module));
    }

    /** Prender numa técnica de Controle imobiliza em vez de agarrar. */
    public static String condition(Effect e, boolean control) {
        return "AGARRADO".equals(e.condition()) && control ? "IMOBILIZADO" : e.condition();
    }

    /** Dispara? Normal: sempre que acerta. "No dado": natural ≥ 17 ou crítico. */
    public static boolean triggers(boolean onDie, Integer natural, boolean critical) {
        return !onDie || critical || (natural != null && natural >= ON_DIE_MIN);
    }

    /** Resistiu? Empate favorece quem resiste (como o Defender). */
    public static boolean resisted(int resistTotal, int nd) {
        return resistTotal >= nd;
    }

    /** ND da resistência: o resultado do teste da técnica, ou 10 + mod. de FOR com Pressão Fixa. */
    public static int nd(int attackTotal, boolean fixedPressure, int attackerStrMod) {
        return fixedPressure ? PRESSURE_BASE_ND + attackerStrMod : attackTotal;
    }

    /** Roubar Tempo: PA perdidos = quantidade do módulo, no máximo 2. */
    public static int stolenPa(int qty) {
        return Math.min(MAX_STOLEN_PA, Math.max(1, qty));
    }

    /** Meio-Cego que sofre a condição de novo fica Cego. */
    public static String escalate(String condition, boolean alreadyHalfBlind) {
        return "MEIO_CEGO".equals(condition) && alreadyHalfBlind ? "CEGO" : condition;
    }

    /** Dano de Derrubada: 2 + Modificador de Peso de quem cai, mínimo 1. */
    public static int takedownDamage(double weightKg) {
        return Math.max(1, 2 + SheetRules.weightMod(weightKg));
    }
}
