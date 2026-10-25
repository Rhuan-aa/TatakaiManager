package com.tatakai.manager.sheet;

import com.tatakai.manager.entity.FatigueBand;
import com.tatakai.manager.service.rules.RollRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RollRules — F5 (RN-ACAO-05, RN-ATQ-01/02)")
class RollRulesTest {

    @Test
    @DisplayName("vantagem líquida: 2d20 maior com +min(n−1,2); desvantagem espelha com o menor")
    void advantage() {
        assertThat(RollRules.d20Count(0)).isEqualTo(1);
        assertThat(RollRules.d20Count(2)).isEqualTo(2);
        assertThat(RollRules.d20Count(-3)).isEqualTo(2);
        assertThat(RollRules.advantageBonus(0)).isZero();
        assertThat(RollRules.advantageBonus(1)).isZero();
        assertThat(RollRules.advantageBonus(2)).isEqualTo(1);
        assertThat(RollRules.advantageBonus(3)).isEqualTo(2);
        assertThat(RollRules.advantageBonus(-1)).isZero();
        assertThat(RollRules.advantageBonus(-3)).isEqualTo(-2);
        assertThat(RollRules.pickNatural(1, List.of(4, 15))).isEqualTo(15);
        assertThat(RollRules.pickNatural(-1, List.of(4, 15))).isEqualTo(4);
        assertThat(RollRules.pickNatural(0, List.of(7))).isEqualTo(7);
        assertThat(RollRules.clampAdvantage(9)).isEqualTo(3);
        assertThat(RollRules.clampAdvantage(-9)).isEqualTo(-3);
    }

    @Test
    @DisplayName("crítico no 20 (margem), falha crítica no 1")
    void critAndFumble() {
        assertThat(RollRules.critical(20, RollRules.CRIT_MARGIN)).isTrue();
        assertThat(RollRules.critical(19, RollRules.CRIT_MARGIN)).isFalse();
        assertThat(RollRules.fumble(1)).isTrue();
        assertThat(RollRules.fumble(2)).isFalse();
    }

    @Test
    @DisplayName("escala de dado da técnica por degrau, limitada a 0..8")
    void diceScale() {
        assertThat(RollRules.diceScale(0).text()).isEqualTo("1d4");
        assertThat(RollRules.diceScale(4).text()).isEqualTo("1d12");
        assertThat(RollRules.diceScale(5).text()).isEqualTo("2d6");
        assertThat(RollRules.diceScale(8).text()).isEqualTo("2d12");
        assertThat(RollRules.diceScale(20).text()).isEqualTo("2d12");
        assertThat(RollRules.diceScale(-2).text()).isEqualTo("1d4");
    }

    @Test
    @DisplayName("penalidade de Desgaste e gasto de SP")
    void fatigueAndSp() {
        assertThat(RollRules.fatiguePenalty(FatigueBand.RESTED)).isZero();
        assertThat(RollRules.fatiguePenalty(FatigueBand.TIRED)).isEqualTo(-1);
        assertThat(RollRules.fatiguePenalty(FatigueBand.DRAINED)).isEqualTo(-2);
        assertThat(RollRules.fatiguePenalty(FatigueBand.EXHAUSTED)).isEqualTo(-3);
        assertThat(RollRules.fatiguePenalty(FatigueBand.COLLAPSE)).isEqualTo(-3);
        assertThat(RollRules.spCost(10, 2, FatigueBand.RESTED)).isEqualTo(6);
        assertThat(RollRules.spCost(3, 5, FatigueBand.RESTED)).isZero();
        assertThat(RollRules.spCost(10, 0, FatigueBand.DRAINED)).isEqualTo(14);
        assertThat(RollRules.spCost(null, 0, FatigueBand.RESTED)).isZero();
    }

    @Test
    @DisplayName("E do dano: floor(FOR/30) + 1 de APEX; mod nulo conta 0; Esboço pela metade")
    void damageHelpers() {
        assertThat(RollRules.strengthExtra(29, false)).isZero();
        assertThat(RollRules.strengthExtra(60, false)).isEqualTo(2);
        assertThat(RollRules.strengthExtra(10, true)).isEqualTo(1);
        assertThat(RollRules.modOrZero(0)).isZero();
        assertThat(RollRules.modOrZero(30)).isEqualTo(3);
        assertThat(RollRules.modOrZero(2)).isEqualTo(-4);
        assertThat(RollRules.sketchHalf(3, true)).isEqualTo(1);
        assertThat(RollRules.sketchHalf(3, false)).isEqualTo(3);
        assertThat(RollRules.signed(-2)).isEqualTo("-2");
        assertThat(RollRules.signed(3)).isEqualTo("+3");
    }
}
