package com.tatakai.manager.combat;

import com.tatakai.manager.entity.Hysteria;
import com.tatakai.manager.service.rules.VitalRules;
import com.tatakai.manager.service.rules.VitalRules.Tier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VitalRules — F6c regras puras")
class VitalRulesTest {

    @Test
    @DisplayName("Faixas de Adrenalina e Histeria só no Ápice")
    void tiers() {
        assertThat(VitalRules.tier(4)).isEqualTo(Tier.NONE);
        assertThat(VitalRules.tier(5)).isEqualTo(Tier.FLOW);
        assertThat(VitalRules.tier(10)).isEqualTo(Tier.APEX);
        assertThat(VitalRules.activeHysteria(9, Hysteria.FURY)).isNull();
        assertThat(VitalRules.activeHysteria(10, Hysteria.FURY)).isEqualTo(Hysteria.FURY);
        assertThat(VitalRules.critMargin(Tier.APEX)).isEqualTo(19);
        assertThat(VitalRules.critMargin(Tier.FLOW)).isEqualTo(20);
    }

    @Test
    @DisplayName("SP: Dissociação ×2, Fluxo −1, nunca abaixo de 0")
    void spCost() {
        assertThat(VitalRules.adjustSp(2, Tier.NONE, false)).isEqualTo(2);
        assertThat(VitalRules.adjustSp(2, Tier.FLOW, false)).isEqualTo(1);
        assertThat(VitalRules.adjustSp(1, Tier.APEX, false)).isZero();
        assertThat(VitalRules.adjustSp(0, Tier.FLOW, false)).isZero();
        assertThat(VitalRules.adjustSp(2, Tier.NONE, true)).isEqualTo(4);
        assertThat(VitalRules.adjustSp(2, Tier.FLOW, true)).isEqualTo(3);
    }

    @Test
    @DisplayName("Crise: metade do HP num turno; HP <= 1/4 com HP > 0; dano massivo")
    void crisis() {
        assertThat(VitalRules.crisisByDamage(10, 20)).isTrue();
        assertThat(VitalRules.crisisByDamage(9, 20)).isFalse();
        assertThat(VitalRules.crisisByLowHp(5, 20)).isTrue();
        assertThat(VitalRules.crisisByLowHp(6, 20)).isFalse();
        assertThat(VitalRules.crisisByLowHp(0, 20)).isFalse();
        assertThat(VitalRules.massiveDamage(10, 20)).isTrue();
        assertThat(VitalRules.massiveDamage(9, 20)).isFalse();
    }

    @Test
    @DisplayName("Perda de PQ: região (sem acento), Impacto/POWER, crítico ×2 em Queixo/Nuca, Muralha −1")
    void kpLoss() {
        assertThat(VitalRules.kpLoss("Tronco", 2, 2, false, false)).isZero();
        assertThat(VitalRules.kpLoss("Queixo", 0, 0, false, false)).isEqualTo(1);
        assertThat(VitalRules.kpLoss("Fígado", 1, 1, false, false)).isEqualTo(3);
        assertThat(VitalRules.kpLoss("Têmpora", 0, 0, true, false)).isEqualTo(1);
        assertThat(VitalRules.kpLoss("nuca", 1, 0, true, false)).isEqualTo(4);
        assertThat(VitalRules.kpLoss("Queixo", 0, 0, false, true)).isZero();
        assertThat(VitalRules.kpLoss("Queixo", 0, 0, true, true)).isEqualTo(1);
    }

    @Test
    @DisplayName("ND de PQ 0 sobe 2 por zeragem; Colapso e Dissociação em 12")
    void thresholds() {
        assertThat(VitalRules.kpZeroNd(0)).isEqualTo(14);
        assertThat(VitalRules.kpZeroNd(2)).isEqualTo(18);
        assertThat(VitalRules.collapse(12)).isTrue();
        assertThat(VitalRules.collapse(11)).isFalse();
        assertThat(VitalRules.dissociated(12)).isTrue();
    }
}
