package com.tatakai.manager.combat;

import com.tatakai.manager.service.WeaponCatalog;
import com.tatakai.manager.service.rules.RollRules;
import com.tatakai.manager.service.rules.WeaponRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeaponRulesTest {

    @Test
    @DisplayName("§42: Dado Adicional, Golpe Pesado, Dispersão, distância, Rajada e trava")
    void rules() {
        assertThat(WeaponRules.extraDie(0)).isNull();
        assertThat(WeaponRules.extraDie(2).text()).isEqualTo("1d4");
        assertThat(WeaponRules.extraDie(9).text()).isEqualTo("1d12");
        assertThat(WeaponRules.heavier(RollRules.Dice.parse("2d8"), 2).text()).isEqualTo("2d12");
        assertThat(WeaponRules.heavier(RollRules.Dice.parse("2d10+1d4"), 1).text()).isEqualTo("2d12+1d6");
        assertThat(WeaponRules.dispersed(RollRules.Dice.parse("6d10"), 1.0).text()).isEqualTo("6d10");
        assertThat(WeaponRules.dispersed(RollRules.Dice.parse("6d10"), 10.0).text()).isEqualTo("5d10");
        assertThat(WeaponRules.dispersed(RollRules.Dice.parse("6d10"), 80.0).text()).isEqualTo("1d10");
        assertThat(WeaponRules.rangeAdvantage(12, 10.0, false)).isZero();
        assertThat(WeaponRules.rangeAdvantage(12, 20.0, false)).isEqualTo(-1);
        assertThat(WeaponRules.rangeAdvantage(12, 30.0, false)).isNull();
        assertThat(WeaponRules.rangeAdvantage(50, 1.0, false)).isEqualTo(-1);
        assertThat(WeaponRules.rangeAdvantage(50, 1.0, true)).isZero();
        assertThat(WeaponRules.burstHits(3, 20, 13, false)).isEqualTo(3);
        assertThat(WeaponRules.burstHits(3, 15, 13, false)).isEqualTo(2);
        assertThat(WeaponRules.burstHits(10, 10, 9, true)).isEqualTo(10);
        assertThat(WeaponRules.jams(1, null)).isTrue();
        assertThat(WeaponRules.jams(3, 3)).isTrue();
        assertThat(WeaponRules.jams(4, 3)).isFalse();
    }

    @Test
    @DisplayName("§42: catálogo — Carga, mãos e tipo marcial")
    void catalog() {
        WeaponCatalog c = new WeaponCatalog();
        assertThat(c.all()).hasSizeGreaterThan(60);
        WeaponCatalog.Def faca = c.find("FACA").orElseThrow();
        assertThat(faca.load()).isEqualTo(3);
        assertThat(faca.martialType()).isEqualTo("Lâmina");
        assertThat(faca.forcesTwoHands()).isFalse();
        WeaponCatalog.Def grande = c.find("ESPADA_GRANDE").orElseThrow();
        assertThat(grande.load()).isEqualTo(8);
        assertThat(grande.forcesTwoHands()).isTrue();
        assertThat(c.find("GLOCK").orElseThrow().forcesTwoHands()).isFalse();
        assertThat(c.find("ARCO_LONGO").orElseThrow().forcesTwoHands()).isTrue();
        assertThat(c.find("ARCO_LONGO").orElseThrow().martialType()).isEqualTo("Artesanais");
        assertThat(c.find("GLOCK").orElseThrow().martialType()).isEqualTo("Pólvora");
        assertThat(c.find("MACA").orElseThrow().load()).isEqualTo(5);
    }
}
