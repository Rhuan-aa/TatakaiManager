package com.tatakai.manager.service.rules;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Proteções e peso de armadura (capítulo Equipamentos, plano §43). Puro. */
public final class EquipmentRules {

    private static final List<String> HEAD = List.of("cabeca", "queixo", "tempora", "nuca", "olho", "nariz", "face",
            "rosto", "orelha", "boca", "testa", "mandibula");
    private static final List<String> ARMS = List.of("braco", "mao", "ombro", "cotovelo", "punho", "dedo");
    private static final List<String> LEGS = List.of("perna", "joelho", "coxa", "canela", "tornozelo", "panturrilha");

    private EquipmentRules() {}

    /** Região do Ataque Localizado → cobertura; sem região, o golpe vai no Tronco. */
    public static String coverage(String region) {
        String r = VitalRules.normalizeRegion(region);
        if (r.isEmpty()) return "TRONCO";
        if (HEAD.stream().anyMatch(r::contains)) return "CABECA";
        if (ARMS.stream().anyMatch(r::contains)) return "BRACOS";
        // "pé" só como palavra inteira ("peito" é tronco)
        if (LEGS.stream().anyMatch(r::contains) || List.of(r.split("\\W+")).stream().anyMatch(w -> w.equals("pe") || w.equals("pes"))) {
            return "PERNAS";
        }
        return "TRONCO";
    }

    /**
     * Redução de uma proteção para o golpe: arma de fogo usa FIREARM (senão DEFAULT); os demais o tipo de dano (senão
     * DEFAULT). Fora da cobertura: 0.
     */
    public static int reduction(Map<String, Integer> protection, List<String> cover, String coverage, String damageType,
                                boolean firearm) {
        if (protection == null || cover == null || !cover.contains(coverage)) return 0;
        Integer v = firearm ? protection.get("FIREARM") : protection.get(damageType);
        if (v == null) v = protection.get("DEFAULT");
        return v == null ? 0 : v;
    }

    /** Proteções na mesma região não somam: vale a maior. */
    public static int best(Collection<Integer> reductions) {
        return reductions.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /** Categoria mais pesada vestida: HEAVY > MEDIUM > LIGHT (null sem proteção). */
    public static String heaviest(Collection<String> categories) {
        if (categories.contains("HEAVY")) return "HEAVY";
        if (categories.contains("MEDIUM")) return "MEDIUM";
        return categories.contains("LIGHT") ? "LIGHT" : null;
    }

    /** Primeira Movimentação do turno: Média −2 m, Pesada −4 m (mínimo 1,5 m). */
    public static double firstMove(double meters, String category) {
        if ("HEAVY".equals(category)) return Math.max(1.5, meters - 4);
        if ("MEDIUM".equals(category)) return Math.max(1.5, meters - 2);
        return meters;
    }

    /** Pesada: toda Ação Maior custa +3 SP. */
    public static int majorSp(String category) {
        return "HEAVY".equals(category) ? 3 : 0;
    }
}
