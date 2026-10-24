package com.tatakai.manager.service.rules;

import com.tatakai.manager.dto.response.AssemblyCheck;
import com.tatakai.manager.entity.ArtBase;
import com.tatakai.manager.entity.ArtCategory;
import com.tatakai.manager.entity.ArtTechnique;
import com.tatakai.manager.entity.AssemblyItem;
import com.tatakai.manager.entity.AssemblyItemKind;
import com.tatakai.manager.entity.AssemblyVariation;
import com.tatakai.manager.entity.Graduation;
import com.tatakai.manager.entity.TechniqueCore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Regras puras de montagem de técnicas e bases (spec §7.3, §7.5, §20). Arredondamento para baixo. */
public final class AssemblyRules {

    private AssemblyRules() {
    }

    /** Módulo exige outro(s) na mesma variação (RN-TEC-05). */
    private static final Map<String, List<String>> REQUIRES = Map.of(
            "Ímpeto", List.of("Investida"),
            "Arremesso", List.of("Derrubar"),
            "Cravar", List.of("Arremesso"),
            "Empurrão Contínuo", List.of("Sequência", "Empurrar"),
            "Acompanhar", List.of("Empurrar"));

    /** Teto de técnica por categoria: Iniciante, Intermediário, Mestre (RN-TEC-03, §20). */
    private static int[] techniqueCeilings(ArtCategory c) {
        return switch (c == null ? ArtCategory.CLASSIC : c) {
            case CLASSIC -> new int[]{8, 12, 16};
            case LOST, CLAN -> new int[]{10, 14, 18};
            // ponytail: VARIATION usa o piso da regra (teto de Criada) até existir parentArtId.
            case CREATED, VARIATION -> new int[]{6, 10, 14};
        };
    }

    /** Teto de base por categoria (RN-ART-05). */
    private static int[] baseCeilings(ArtCategory c) {
        return switch (c == null ? ArtCategory.CLASSIC : c) {
            case CLASSIC -> new int[]{5, 7, 9};
            case LOST, CLAN -> new int[]{6, 8, 10};
            case CREATED, VARIATION -> new int[]{4, 6, 8};
        };
    }

    public static int techniqueCeiling(ArtCategory c, Graduation g) {
        return g == null || g == Graduation.GENERIC ? 3 : techniqueCeilings(c)[g.ordinal() - 1];
    }

    public static int baseCeiling(ArtCategory c, Graduation g) {
        return g == null || g == Graduation.GENERIC ? 2 : baseCeilings(c)[g.ordinal() - 1];
    }

    public static int raw(List<AssemblyItem> items) {
        return items.stream().mapToInt(AssemblyItem::getCost).filter(v -> v > 0).sum();
    }

    /** C_final = C_bruta − min(Σ|limitações|, floor(C_bruta/2)). */
    public static int finalComplexity(List<AssemblyItem> items) {
        int raw = raw(items);
        int limits = items.stream().mapToInt(AssemblyItem::getCost).filter(v -> v < 0).map(Math::abs).sum();
        return raw - Math.min(limits, raw / 2);
    }

    /** RN-TEC-02 / §20. */
    public static int spCost(ArtCategory c, int raw) {
        return c == ArtCategory.LOST || c == ArtCategory.CLAN ? Math.max(0, 2 + raw) : 4 + raw;
    }

    public static int trainingPoints(int finalComplexity) {
        return Math.max(2, finalComplexity);
    }

    public static AssemblyCheck checkTechnique(ArtCategory category, ArtTechnique t) {
        List<AssemblyVariation> vars = t.getVariations() == null ? List.of() : t.getVariations();
        List<AssemblyItem> extras = t.getExtras() == null ? List.of() : t.getExtras();
        List<String> issues = new ArrayList<>();
        if (vars.size() > 2) issues.add("Uma técnica tem no máximo 2 variações");

        int rawMax = 0;
        int finalMax = 0;
        List<Integer> sp = new ArrayList<>();
        for (AssemblyVariation v : vars) {
            List<AssemblyItem> items = v.getItems() == null ? List.of() : v.getItems();
            int raw = raw(items);
            rawMax = Math.max(rawMax, raw);
            finalMax = Math.max(finalMax, finalComplexity(items));
            sp.add(spCost(category, raw));
            validateVariation(v, items, t.getGraduation(), issues);
        }
        int fin = (vars.size() > 1 ? finalMax + 1 : finalMax) + raw(extras);
        int ceiling = techniqueCeiling(category, t.getGraduation());
        if (fin > ceiling) issues.add("Complexidade " + fin + " acima do teto " + ceiling + " da graduação");
        return new AssemblyCheck(rawMax + raw(extras), fin, sp, trainingPoints(fin), ceiling, issues);
    }

    public static AssemblyCheck checkBase(ArtCategory category, ArtBase b) {
        List<AssemblyItem> items = b.getItems() == null ? List.of() : b.getItems();
        int fin = finalComplexity(items);
        int ceiling = baseCeiling(category, b.getGraduation());
        List<String> issues = new ArrayList<>();
        if (fin > ceiling) issues.add("Complexidade " + fin + " acima do teto " + ceiling + " da graduação");
        return new AssemblyCheck(raw(items), fin, List.of(), trainingPoints(fin), ceiling, issues);
    }

    private static void validateVariation(AssemblyVariation v, List<AssemblyItem> items, Graduation g,
                                          List<String> issues) {
        String where = v.getName() == null || v.getName().isBlank() ? "" : " (" + v.getName() + ")";
        Set<String> names = items.stream().map(AssemblyRules::nameOf).collect(Collectors.toSet());
        long effects = items.stream().filter(i -> i.getKind() == AssemblyItemKind.EFFECT).count();
        if (effects > 2 && !names.contains("Pés no Ar")) issues.add("Mais de 2 Efeitos na variação" + where);

        REQUIRES.forEach((module, needs) -> {
            if (names.contains(module) && !names.containsAll(needs)) {
                issues.add(module + " exige " + String.join(" e ", needs) + where);
            }
        });
        if (names.contains("Sufocar") && v.getCore() != TechniqueCore.CONTROL) {
            issues.add("Sufocar exige núcleo Controle" + where);
        }
        if (names.contains("Círculo") && names.contains("Varredura")) {
            issues.add("Círculo não pode ser combinado com Varredura" + where);
        }
        if (names.contains("Paralisar") && g != Graduation.MASTER) {
            issues.add("Paralisar exige graduação Mestre" + where);
        }
        if (names.contains("Mutilar") && (g == null || g.ordinal() < Graduation.INTERMEDIATE.ordinal())) {
            issues.add("Mutilar exige graduação Intermediário ou maior" + where);
        }
    }

    private static String nameOf(AssemblyItem i) {
        return i.getModule() != null ? i.getModule() : i.getLabel();
    }
}
