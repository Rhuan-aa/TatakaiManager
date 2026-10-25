package com.tatakai.manager.combat;

import com.tatakai.manager.service.rules.EnvironmentRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EnvironmentRules — Queimando, Congelando, Envenenado (§33)")
class EnvironmentRulesTest {

    @Test
    @DisplayName("níveis por intensidade (limitados à faixa) e ND do Congelando subindo +2")
    void levels() {
        assertThat(EnvironmentRules.level(EnvironmentRules.BURN, 2).dice()).isEqualTo("2d6");
        assertThat(EnvironmentRules.level(EnvironmentRules.BURN, 2).nd()).isEqualTo(15);
        assertThat(EnvironmentRules.level(EnvironmentRules.BURN, 9).label()).isEqualTo("Avassalador");
        assertThat(EnvironmentRules.level(EnvironmentRules.POISON, null).sp()).isTrue();
        assertThat(EnvironmentRules.level(EnvironmentRules.POISON, 3).nd()).isEqualTo(17);
        assertThat(EnvironmentRules.freezeNd(0)).isEqualTo(14);
        assertThat(EnvironmentRules.freezeNd(2)).isEqualTo(18);
    }
}
