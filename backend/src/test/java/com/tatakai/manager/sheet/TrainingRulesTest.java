package com.tatakai.manager.sheet;

import com.tatakai.manager.entity.BodyType;
import com.tatakai.manager.entity.FatigueBand;
import com.tatakai.manager.entity.TrainingVenue;
import com.tatakai.manager.service.rules.TrainingRules;
import com.tatakai.manager.service.rules.TrainingRules.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingRulesTest {

    private static Outcome train(String target, int nthToday, int fulls, FatigueBand band) {
        return TrainingRules.train(target, 25, BodyType.MESOMORPH, nthToday, fulls, 1, band, TrainingVenue.FIELD);
    }

    @Test
    @DisplayName("F4a — RN-PROG-01: base + floor(modQ(TAL)/2) + tipo corporal; CAR/HP/SP base 2; PQ a cada 4º")
    void baseGain() {
        assertThat(TrainingRules.baseGain("STR", 25, BodyType.MESOMORPH, 1)).isEqualTo(1 + 1 + 1);
        assertThat(TrainingRules.baseGain("DEX", 25, BodyType.MESOMORPH, 1)).isEqualTo(2);
        assertThat(TrainingRules.baseGain("CHA", 5, null, 1)).isEqualTo(2);
        assertThat(TrainingRules.baseGain("KP", 25, null, 3)).isZero();
        assertThat(TrainingRules.baseGain("KP", 25, null, 4)).isEqualTo(1);
    }

    @Test
    @DisplayName("F4a — RN-PROG-02: 2º treino do dia +1 Desgaste; 3º não rende")
    void overload() {
        assertThat(train("STR", 1, 0, FatigueBand.RESTED).fatigue()).isEqualTo(1);
        assertThat(train("STR", 2, 0, FatigueBand.RESTED).fatigue()).isEqualTo(2);
        assertThat(train("STR", 3, 0, FatigueBand.RESTED).gain()).isZero();
    }

    @Test
    @DisplayName("F4a — RN-PROG-03/05: Campo = 2 Plenos; excedente Parcial = metade; Esgotado tira 1 Pleno; Colapso bloqueia")
    void fullAndPartial() {
        assertThat(train("STR", 1, 1, FatigueBand.RESTED).full()).isTrue();
        Outcome partial = train("STR", 1, 2, FatigueBand.RESTED);
        assertThat(partial.full()).isFalse();
        assertThat(partial.gain()).isEqualTo(1);
        assertThat(train("STR", 1, 1, FatigueBand.DRAINED).full()).isFalse();
        assertThat(train("MEN", 1, 9, FatigueBand.DRAINED).gain()).isEqualTo(1);
        assertThat(train("STR", 1, 0, FatigueBand.EXHAUSTED).full()).isFalse();
        assertThat(train("STR", 1, 0, FatigueBand.COLLAPSE).lost()).isTrue();
    }

    @Test
    @DisplayName("F4a — RN-ATR-06: Barreiras truncam em 3, 6 e 9")
    void barriers() {
        assertThat(TrainingRules.barrierCap(2, 3)).isEqualTo(1);
        assertThat(TrainingRules.barrierCap(6, 2)).isZero();
        assertThat(TrainingRules.barrierCap(8, 3)).isEqualTo(1);
        assertThat(TrainingRules.barrierCap(10, 3)).isEqualTo(3);
    }

    @Test
    @DisplayName("F4a — RN-PROG-04: Precário reduz 1 por treino, mínimo 1")
    void precarious() {
        assertThat(TrainingRules.precarious(3)).isEqualTo(2);
        assertThat(TrainingRules.precarious(1)).isEqualTo(1);
    }
}
