package com.tatakai.manager.service.rules;

import com.tatakai.manager.entity.AssemblyItem;
import com.tatakai.manager.entity.AssemblyItemKind;
import com.tatakai.manager.entity.ReactionType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Técnicas de núcleo **Reação** e **Preparação** (Criação Marcial, módulos de Defesa e Preparação, plano §38). Puro:
 * lê os itens da montagem; o valor e o escopo do Reforço vêm do rótulo ("Reforço +3 em Defender").
 */
public final class TechniqueRules {

    /** Mitigação do Defender em graus: falha 25%, sucesso 50/75/100%. */
    private static final int[] GRADES = {0, 25, 50, 75, 100};

    private TechniqueRules() {}

    /** Técnica de Reação: tipo de reação que ela substitui, bônus e módulos que agem no resultado. */
    public record Reaction(ReactionType type, int bonus, boolean escalated, boolean punitive, boolean opening,
                           boolean absorb, boolean breath, boolean interrupt, List<String> triggers) {}

    /** Técnica de Preparação: Reforço por tipo de reação, Carga (+dano no próximo golpe), Leitura, Sustentação. */
    public record Preparation(Map<ReactionType, Integer> reinforce, int charge, boolean reading, boolean sustain,
                              boolean breath) {}

    public static Reaction reaction(List<AssemblyItem> items) {
        ReactionType type = null;
        int bonus = 0;
        for (AssemblyItem i : items) {
            if (!"Reforço".equals(i.getModule())) continue;
            ReactionType t = reactionOf(BaseRules.scope(i.getLabel()));
            if (type == null && t != null) type = t;
            bonus += (int) value(i);
        }
        if (type == null) type = ReactionType.DEFEND; // Redução Escalonada / Bloqueio Punitivo são do Defender
        return new Reaction(type, bonus, has(items, "Redução Escalonada"), has(items, "Bloqueio Punitivo"),
                has(items, "Abertura"), has(items, "Absorção"), has(items, "Fôlego Tático"), has(items, "Interromper"),
                items.stream().filter(i -> i.getKind() == AssemblyItemKind.LIMITATION && i.getLabel() != null
                        && i.getLabel().startsWith("Gatilho")).map(AssemblyItem::getLabel).toList());
    }

    public static Preparation preparation(List<AssemblyItem> items) {
        Map<ReactionType, Integer> reinforce = new EnumMap<>(ReactionType.class);
        int charge = 0;
        for (AssemblyItem i : items) {
            if ("Reforço".equals(i.getModule())) {
                ReactionType t = reactionOf(BaseRules.scope(i.getLabel()));
                reinforce.merge(t == null ? ReactionType.DEFEND : t, (int) value(i), Integer::sum);
            }
            if ("Carga".equals(i.getModule())) charge += (int) value(i);
        }
        return new Preparation(reinforce, charge, has(items, "Leitura"), has(items, "Sustentação"),
                has(items, "Fôlego Tático"));
    }

    /** Redução Escalonada: a mitigação do Defender sobe um grau (25→50→75→100). */
    public static int stepUp(int mitigationPct) {
        for (int g : GRADES) if (g > mitigationPct) return g;
        return 100;
    }

    private static ReactionType reactionOf(BaseRules.Scope s) {
        return switch (s) {
            case DEFEND -> ReactionType.DEFEND;
            case DODGE -> ReactionType.DODGE;
            case COUNTER -> ReactionType.COUNTER;
            default -> null;
        };
    }

    private static double value(AssemblyItem i) {
        String l = i.getLabel();
        return l != null && l.matches(".*\\d.*") ? BaseRules.value(l) : i.getQty();
    }

    private static boolean has(List<AssemblyItem> items, String module) {
        return items.stream().anyMatch(i -> module.equals(i.getModule()));
    }
}
