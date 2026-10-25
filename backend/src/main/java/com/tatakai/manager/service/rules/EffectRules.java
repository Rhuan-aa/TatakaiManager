package com.tatakai.manager.service.rules;

import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.ConditionExpiry;

import java.util.Map;
import java.util.Optional;

/**
 * Efeitos de módulo aplicados no acerto (§26.1, contrato no plano §30). Puro.
 * Resistência: o alvo rola o atributo contra o **total do ataque da técnica** (Regras Gerais das Condições).
 * "No dado": só dispara com d20 natural ≥ 17; em crítico o efeito entra sem teste.
 */
public final class EffectRules {

    public static final int ON_DIE_MIN = 17;

    /**
     * condition null = sem condição (só registra, ex.: Empurrar sem grade); expiry null = padrão do catálogo.
     * stealsPa: qtd do módulo vira perda de PA no próximo turno. wounds: qtd vira Feridas abertas (sem teste).
     */
    public record Effect(String condition, Attribute resist, ConditionExpiry expiry, Integer turns, boolean stealsPa,
                         boolean takedown, boolean wounds) {
        static Effect cond(String key, Attribute a) { return new Effect(key, a, null, null, false, false, false); }
    }

    // ponytail: atributo de resistência por efeito deduzido das técnicas do livro (Liver Shot: FOR; Khao Loy: CON;
    // Empurrar: FOR). Trocar aqui quando o capítulo de Criação Marcial definir.
    private static final Map<String, Effect> EFFECTS = Map.ofEntries(
            Map.entry("Derrubar", new Effect("DERRUBADO", Attribute.STR, null, null, false, true, false)),
            Map.entry("Quebrar Base", Effect.cond("QUEBRA_DE_BASE", Attribute.STR)),
            Map.entry("Empurrar", Effect.cond(null, Attribute.STR)),
            Map.entry("Prender", Effect.cond("AGARRADO", Attribute.STR)),
            Map.entry("Desorientar", Effect.cond("DESORIENTADO", Attribute.CON)),
            Map.entry("Expor", Effect.cond("VULNERAVEL", Attribute.DEX)),
            Map.entry("Atordoar", Effect.cond("ATORDOADO", Attribute.CON)),
            Map.entry("Paralisar", new Effect("PARALISADO", Attribute.CON, ConditionExpiry.COUNTER, 1, false, false, false)),
            Map.entry("Cegar", Effect.cond("MEIO_CEGO", Attribute.CON)),
            Map.entry("Sufocar", Effect.cond("SUFOCANDO", Attribute.CON)),
            Map.entry("Roubar Tempo", new Effect(null, Attribute.CON, null, null, true, false, false)),
            Map.entry("Abrir Ferida", new Effect(null, null, null, null, false, false, true)));

    private EffectRules() {}

    /** Efeito automático do módulo; vazio = o Mestre aplica à mão (Interromper, Aleijar, Marcar…). */
    public static Optional<Effect> of(String module) {
        return Optional.ofNullable(module == null ? null : EFFECTS.get(module));
    }

    /** Dispara? Normal: sempre que acerta. "No dado": natural ≥ 17 ou crítico. */
    public static boolean triggers(boolean onDie, Integer natural, boolean critical) {
        return !onDie || critical || (natural != null && natural >= ON_DIE_MIN);
    }

    /** Resistiu? Empate favorece quem resiste (como o Defender). */
    public static boolean resisted(int resistTotal, int attackTotal) {
        return resistTotal >= attackTotal;
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
