package com.tatakai.manager.combat;

import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.MartialType;
import com.tatakai.manager.service.rules.GrappleRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GrappleRules — agarre dos Ataques Básicos (§39)")
class GrappleRulesTest {

    @Test
    @DisplayName("fuga: custo, desvantagem e atributos por condição")
    void escape() {
        assertThat(GrappleRules.escapeCost("AGARRADO")).isEqualTo(1);
        assertThat(GrappleRules.escapeCost("IMOBILIZADO")).isEqualTo(3);
        assertThat(GrappleRules.escapeDisadvantage("AGARRADO", null)).isTrue();
        assertThat(GrappleRules.escapeDisadvantage("CLINCH", null)).isFalse();
        assertThat(GrappleRules.escapeDisadvantage("IMOBILIZADO", "CONSTRICAO")).isFalse();
        assertThat(GrappleRules.escapeDisadvantage("IMOBILIZADO", null)).isTrue();
        assertThat(GrappleRules.escapeAllows("AGARRADO", Attribute.DEX, MartialType.STRIKING)).isFalse();
        assertThat(GrappleRules.escapeAllows("AGARRADO", Attribute.DEX, MartialType.SWEEP)).isTrue();
        assertThat(GrappleRules.escapeAllows("IMOBILIZADO", Attribute.DEX, MartialType.STRIKING)).isTrue();
        assertThat(GrappleRules.escapeAllows("CLINCH", Attribute.DEX, MartialType.TAKEDOWN)).isFalse();
    }

    @Test
    @DisplayName("competição (empate resiste), Clinch pelo tronco, pânico e dano cerebral")
    void misc() {
        assertThat(GrappleRules.wins(15, 14)).isTrue();
        assertThat(GrappleRules.wins(14, 14)).isFalse();
        assertThat(GrappleRules.clinch(GrappleRules.Grip.TORSO)).isTrue();
        assertThat(GrappleRules.clinch(GrappleRules.Grip.ARM)).isFalse();
        assertThat(GrappleRules.panicNd(0)).isEqualTo(12);
        assertThat(GrappleRules.panicNd(2)).isEqualTo(16);
        assertThat(GrappleRules.brainDamage(40)).isEqualTo(10);
    }
}
