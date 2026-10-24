package com.tatakai.manager.catalog;

import com.tatakai.manager.entity.Mastery;
import com.tatakai.manager.entity.MasteryBonus;
import com.tatakai.manager.entity.MoveKind;
import com.tatakai.manager.service.rules.MasteryRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MasteryRulesTest {

    @Test
    @DisplayName("F3b — RN-MAE-01: Oi-Zuki (PT 6) custa 6 / 9 / 12")
    void levelCosts() {
        assertThat(MasteryRules.nextLevelCost(6, Mastery.STANDARD)).isEqualTo(6);
        assertThat(MasteryRules.nextLevelCost(6, Mastery.IMPROVED)).isEqualTo(9);
        assertThat(MasteryRules.nextLevelCost(6, Mastery.SPECIALIZED)).isEqualTo(12);
        assertThat(MasteryRules.nextLevelCost(6, Mastery.MASTERED)).isNull();
        assertThat(MasteryRules.nextLevelCost(6, Mastery.SKETCH)).isNull();
    }

    @Test
    @DisplayName("F3b — §7.7: ganho de treino = 1 + floor(modQ(TAL)/2)")
    void trainingGain() {
        assertThat(MasteryRules.trainingGain(2)).isEqualTo(1);   // mod negativo vira 0
        assertThat(MasteryRules.trainingGain(25)).isEqualTo(2);  // mod 2
        assertThat(MasteryRules.trainingGain(45)).isEqualTo(3);  // mod 4
    }

    @Test
    @DisplayName("F3b — RN-MAE-03: bonificação por tipo e Coringa até 2")
    void bonuses() {
        assertThat(MasteryRules.bonusAllowed(MoveKind.TECHNIQUE, MasteryBonus.DAMAGE, List.of())).isTrue();
        assertThat(MasteryRules.bonusAllowed(MoveKind.BASE, MasteryBonus.DAMAGE, List.of())).isFalse();
        assertThat(MasteryRules.bonusAllowed(MoveKind.BASE, MasteryBonus.REDUCTION, List.of())).isTrue();
        assertThat(MasteryRules.bonusAllowed(MoveKind.TECHNIQUE, MasteryBonus.APEX, List.of())).isFalse();
        assertThat(MasteryRules.bonusAllowed(MoveKind.TECHNIQUE, MasteryBonus.JOKER,
                List.of(MasteryBonus.JOKER, MasteryBonus.JOKER))).isFalse();
    }
}
