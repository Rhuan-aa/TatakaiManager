package com.tatakai.manager.catalog.service.rules;

import com.tatakai.manager.catalog.entity.AssemblyItem;
import com.tatakai.manager.combat.entity.Floor;
import com.tatakai.manager.sheet.entity.Attribute;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Módulos e limitações de técnica com efeito automático (Criação Marcial, plano §41). Puro: lê os itens da montagem. */
public final class ModuleRules {

    /** Golpe no Fôlego: o catálogo chama de "Fôlego (só no SP)" (custo 0) e "Fôlego" (custo 2). */
    public enum Breath { NONE, SP_ONLY, BOTH }

    /** Risco: errar derruba o usuário (−1) ou o fere com a própria técnica ("Risco alto", −2). */
    public enum Risk { NONE, FALL, SELF_DAMAGE }

    /** Atributo Alternativo: qual atributo e se troca o teste (senão, a Força do dano). */
    public record AltAttribute(Attribute attribute, boolean test) {}

    /** Alvos sem Varredura/Círculo. */
    public static final int SINGLE = 1;
    public static final int SWEEP = 2;
    /** Ímpeto: +2 a cada 1,5 m, até +6. */
    public static final int IMPETUS_MAX = 6;
    /** Impacto do Esmagar: 1d4+3 e +1 PQ. */
    public static final String CRUSH_DAMAGE = "1d4";
    public static final int CRUSH_FLAT = 3;

    private ModuleRules() {}

    public static boolean has(List<AssemblyItem> items, String module) {
        return items.stream().anyMatch(i -> module.equals(i.getModule()));
    }

    private static AssemblyItem find(List<AssemblyItem> items, String module) {
        return items.stream().filter(i -> module.equals(i.getModule())).findFirst().orElse(null);
    }

    private static String text(AssemblyItem i) {
        String t = (i.getLabel() == null ? "" : i.getLabel()) + " " + (i.getNote() == null ? "" : i.getNote());
        return Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    public static Breath breath(List<AssemblyItem> items) {
        for (AssemblyItem i : items) {
            String m = i.getModule();
            if (m == null) continue;
            if (m.equals("Fôlego (só no SP)")) return Breath.SP_ONLY;
            if (m.equals("Fôlego") || m.startsWith("Golpe no Fôlego")) return i.getCost() == 0 ? Breath.SP_ONLY : Breath.BOTH;
        }
        return Breath.NONE;
    }

    /** Turnos do Dano Persistente: 1 + 1 por ponto de custo acima de 3; 0 sem o módulo. */
    public static int persistentTurns(List<AssemblyItem> items) {
        AssemblyItem i = find(items, "Dano Persistente");
        return i == null ? 0 : 1 + Math.max(0, i.getCost() - 3);
    }

    public static AltAttribute altAttribute(List<AssemblyItem> items) {
        AssemblyItem i = find(items, "Atributo Alternativo");
        if (i == null) return null;
        String t = text(i);
        Attribute a = t.matches(".*\\bint.*") ? Attribute.INT : Attribute.DEX;
        return new AltAttribute(a, t.contains("teste") || t.contains("acerto"));
    }

    /** Massa no acerto (rótulo/nota com "acerto"): metade do Mod. de Peso, entre 0 e 3; 0 se for no dano. */
    public static int massHit(List<AssemblyItem> items, int weightMod) {
        AssemblyItem i = find(items, "Massa");
        return i == null || !text(i).contains("acerto") ? 0 : Math.max(0, Math.min(3, weightMod / 2));
    }

    /** Massa no dano: Mod. de Peso (mín. 0); 0 se for no acerto. */
    public static int massDamage(List<AssemblyItem> items, int weightMod) {
        AssemblyItem i = find(items, "Massa");
        return i == null || text(i).contains("acerto") ? 0 : Math.max(0, weightMod);
    }

    public static int impetus(Double runMeters) {
        if (runMeters == null || runMeters <= 0) return 0;
        return Math.min(IMPETUS_MAX, 2 * (int) Math.floor(runMeters / 1.5 + 1e-9));
    }

    public static int maxTargets(List<AssemblyItem> items) {
        if (has(items, "Círculo")) return Integer.MAX_VALUE;
        return has(items, "Varredura") ? SWEEP : SINGLE;
    }

    /** Tardia: rodada mínima (3, 5 ou 7); 1 sem a limitação. */
    public static int minRound(List<AssemblyItem> items) {
        int steps = 0;
        for (AssemblyItem i : items) {
            String m = i.getModule();
            if (m != null && m.startsWith("Tardia")) steps = Math.max(steps, Math.max(1, Math.abs(i.getCost())));
        }
        return 1 + 2 * steps;
    }

    /** Previsível: −2 por uso anterior contra o mesmo alvo. */
    public static int predictable(List<AssemblyItem> items, int priorUses) {
        return has(items, "Previsível") ? -2 * Math.max(0, priorUses) : 0;
    }

    public static Risk risk(List<AssemblyItem> items) {
        if (has(items, "Risco alto")) return Risk.SELF_DAMAGE;
        AssemblyItem i = find(items, "Risco");
        if (i == null) return Risk.NONE;
        return i.getCost() <= -2 ? Risk.SELF_DAMAGE : Risk.FALL;
    }

    /**
     * Exige Base: null sem a limitação; "" = qualquer base; senão o nome da base (rótulo/nota sem o prefixo).
     */
    public static String requiredBase(List<AssemblyItem> items) {
        AssemblyItem i = find(items, "Exige Base");
        if (i == null) return null;
        String raw = i.getNote() != null && !i.getNote().isBlank() ? i.getNote() : i.getLabel() == null ? "" : i.getLabel();
        return raw.replaceFirst("(?i)^\\s*exige base\\s*[:\\-–(]?\\s*", "").replaceAll("\\)\\s*$", "").trim();
    }

    /** Encadear: gasto da técnica nova +25%, arredondado para cima. */
    public static int chainSp(int sp) {
        return (int) Math.ceil(sp * 1.25);
    }

    /** Dano de Arremesso pelo chão (Cravar = sempre duro): dados ou null (macio). */
    public static String throwDice(Floor floor, boolean spike) {
        Floor f = spike ? Floor.HARD : floor == null ? Floor.STANDARD : floor;
        return switch (f) {
            case SOFT -> null;
            case STANDARD -> "1d4";
            case HARD -> "2d4";
        };
    }

    /** Arremesso em chão duro (ou Cravar) também tira 1 PQ. */
    public static int throwKp(Floor floor, boolean spike) {
        return spike || floor == Floor.HARD ? 1 : 0;
    }
}
