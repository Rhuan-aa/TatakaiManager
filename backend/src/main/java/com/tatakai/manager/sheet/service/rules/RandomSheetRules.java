package com.tatakai.manager.sheet.service.rules;

import com.tatakai.manager.catalog.entity.Graduation;
import com.tatakai.manager.catalog.entity.TraitCategory;
import com.tatakai.manager.catalog.entity.TraitRarity;
import com.tatakai.manager.sheet.entity.Attribute;
import com.tatakai.manager.sheet.entity.BodyType;
import com.tatakai.manager.sheet.entity.Dominance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Gerador de ficha aleatória do Mestre (§51.9): perfil escala atributos e EL; arte foca atributos. Puro e testável. */
public final class RandomSheetRules {

    private RandomSheetRules() {}

    /** bonus = somado ao 1d12 de cada atributo; el = EL inicial (Keiken); traits = quantidade padrão; grad = graduação padrão da arte. */
    public enum Profile {
        NOVICE(0, 0, 1, Graduation.BEGINNER),
        EXPERIENCED(3, 8, 2, Graduation.BEGINNER),
        MASTER(6, 20, 3, Graduation.INTERMEDIATE),
        RIVAL(8, 32, 3, Graduation.INTERMEDIATE),
        BOSS(11, 58, 4, Graduation.MASTER);

        public final int bonus;
        public final int el;
        public final int traits;
        public final Graduation grad;

        Profile(int bonus, int el, int traits, Graduation grad) {
            this.bonus = bonus;
            this.el = el;
            this.traits = traits;
            this.grad = grad;
        }
    }

    public record Generated(BodyType bodyType, Dominance dominance, double heightM,
                            Map<Attribute, Integer> attributes, int el) {}

    /** Item mínimo do catálogo para sortear traços sem depender da entidade Mongo. */
    public record TraitRef(UUID id, TraitRarity rarity, TraitCategory category, boolean developable) {}

    private static final int FOCUS_BONUS = 3;

    /** Atributos que a arte privilegia, lidos do tipo marcial (texto do catálogo); sem pista → dois sorteados. */
    public static List<Attribute> focusFor(String martialType, Random rnd) {
        String t = martialType == null ? "" : martialType.toLowerCase();
        if (t.contains("troca") || t.contains("golpe")) return List.of(Attribute.STR, Attribute.DEX);
        if (t.contains("agarr") || t.contains("luta") || t.contains("arremes")) return List.of(Attribute.STR, Attribute.CON);
        if (t.contains("esquiv") || t.contains("contra")) return List.of(Attribute.DEX, Attribute.INT);
        List<Attribute> all = new ArrayList<>(List.of(Attribute.STR, Attribute.DEX, Attribute.CON, Attribute.INT, Attribute.MEN));
        Collections.shuffle(all, rnd);
        return all.subList(0, 2);
    }

    public static Generated generate(Profile profile, List<Attribute> focus, Random rnd) {
        Map<Attribute, Integer> attrs = new EnumMap<>(Attribute.class);
        for (Attribute a : Attribute.values()) {
            int v = 1 + rnd.nextInt(12) + profile.bonus;
            if (focus != null && focus.contains(a)) v += FOCUS_BONUS;
            attrs.put(a, v);
        }
        BodyType[] bodies = BodyType.values();
        int d = rnd.nextInt(100);
        Dominance dom = d < 80 ? Dominance.RIGHT : d < 95 ? Dominance.LEFT : Dominance.AMBIDEXTROUS;
        double height = Math.round((1.5 + rnd.nextDouble() * 0.5) * 100) / 100.0;
        return new Generated(bodies[rnd.nextInt(bodies.length)], dom, height, attrs, profile.el);
    }

    /** Uma linha de sorteio de traços (filtros nulos = qualquer). */
    public record TraitLine(int count, TraitRarity rarity, TraitCategory category, Boolean developable) {}

    /**
     * Rola todas as linhas em sequência sobre o mesmo catálogo, sem repetir traço entre linhas. Linha que não alcança a
     * quantidade pedida entrega o que houver.
     */
    public static List<TraitRef> pickTraits(List<TraitRef> catalog, List<TraitLine> lines, Random rnd) {
        List<TraitRef> out = new ArrayList<>();
        for (TraitLine l : lines) {
            List<TraitRef> pool = new ArrayList<>(catalog.stream()
                    .filter(t -> !out.contains(t))
                    .filter(t -> l.rarity() == null || t.rarity() == l.rarity())
                    .filter(t -> l.category() == null || t.category() == l.category())
                    .filter(t -> l.developable() == null || t.developable() == l.developable())
                    .toList());
            Collections.shuffle(pool, rnd);
            out.addAll(pool.subList(0, Math.max(0, Math.min(l.count(), pool.size()))));
        }
        return out;
    }
}
