package com.tatakai.manager.sheet;

import com.tatakai.manager.catalog.entity.TraitCategory;
import com.tatakai.manager.catalog.entity.TraitRarity;
import com.tatakai.manager.sheet.entity.Attribute;
import com.tatakai.manager.sheet.service.rules.RandomSheetRules;
import com.tatakai.manager.sheet.service.rules.RandomSheetRules.Profile;
import com.tatakai.manager.sheet.service.rules.RandomSheetRules.TraitRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Ficha aleatória (§51.9)")
class RandomSheetRulesTest {

    private static final TraitCategory ANY = TraitCategory.values()[0];

    @Test
    @DisplayName("perfil escala os atributos (1d12 + bônus) e o EL")
    void profileScalesAttributes() {
        var boss = RandomSheetRules.generate(Profile.BOSS, null, new Random(1));
        var novice = RandomSheetRules.generate(Profile.NOVICE, null, new Random(1));
        for (Attribute a : Attribute.values()) {
            assertThat(novice.attributes().get(a)).isBetween(1, 12);
            assertThat(boss.attributes().get(a)).isBetween(12, 23);
        }
        assertThat(boss.el()).isGreaterThan(novice.el());
    }

    @Test
    @DisplayName("a arte soma +3 nos atributos focados; tipo de trocação foca FOR e DES")
    void focusAddsBonus() {
        var focus = RandomSheetRules.focusFor("Trocação", new Random(2));
        assertThat(focus).containsExactly(Attribute.STR, Attribute.DEX);
        var plain = RandomSheetRules.generate(Profile.NOVICE, null, new Random(7));
        var focused = RandomSheetRules.generate(Profile.NOVICE, focus, new Random(7));
        assertThat(focused.attributes().get(Attribute.STR)).isEqualTo(plain.attributes().get(Attribute.STR) + 3);
        assertThat(focused.attributes().get(Attribute.CON)).isEqualTo(plain.attributes().get(Attribute.CON));
    }

    @Test
    @DisplayName("traços: cada linha com seus filtros, tudo rolado no fim e sem repetir entre linhas")
    void picksTraitsByLines() {
        var common = new TraitRef(UUID.randomUUID(), TraitRarity.COMMON, ANY, false);
        var commonDev = new TraitRef(UUID.randomUUID(), TraitRarity.COMMON, ANY, true);
        var rare = new TraitRef(UUID.randomUUID(), TraitRarity.RARE, ANY, true);
        var cat = List.of(common, commonDev, rare);

        var lines = List.of(new RandomSheetRules.TraitLine(1, TraitRarity.RARE, null, null),
                new RandomSheetRules.TraitLine(2, TraitRarity.COMMON, null, null),
                new RandomSheetRules.TraitLine(3, null, null, null));
        var out = RandomSheetRules.pickTraits(cat, lines, new Random(3));
        assertThat(out).containsExactlyInAnyOrder(rare, common, commonDev); // 3ª linha já não tem o que sortear
        assertThat(out.get(0)).isEqualTo(rare);

        assertThat(RandomSheetRules.pickTraits(cat, List.of(new RandomSheetRules.TraitLine(5, null, null, true)),
                new Random(3))).containsExactlyInAnyOrder(commonDev, rare);
    }
}
