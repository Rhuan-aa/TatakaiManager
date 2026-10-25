package com.tatakai.manager.combat;

import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.service.rules.EffectRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EffectRules — efeitos de módulo no acerto (§30)")
class EffectRulesTest {

    @Test
    @DisplayName("mapa: Derrubar/FOR derruba; Atordoar/CON; efeito sem regra fica manual")
    void mapping() {
        assertThat(EffectRules.of("Derrubar")).hasValueSatisfying(e -> {
            assertThat(e.condition()).isEqualTo("DERRUBADO");
            assertThat(e.resist()).isEqualTo(Attribute.STR);
            assertThat(e.takedown()).isTrue();
        });
        assertThat(EffectRules.of("Atordoar").get().resist()).isEqualTo(Attribute.CON);
        assertThat(EffectRules.of("Roubar Tempo").get().stealsPa()).isTrue();
        assertThat(EffectRules.of("Marcar")).isEmpty();
        assertThat(EffectRules.of(null)).isEmpty();
    }

    @Test
    @DisplayName("no dado: só com natural ≥ 17 ou crítico; normal sempre dispara")
    void triggers() {
        assertThat(EffectRules.triggers(false, 3, false)).isTrue();
        assertThat(EffectRules.triggers(true, 16, false)).isFalse();
        assertThat(EffectRules.triggers(true, 17, false)).isTrue();
        assertThat(EffectRules.triggers(true, 5, true)).isTrue();
        assertThat(EffectRules.triggers(true, null, false)).isFalse();
    }

    @Test
    @DisplayName("resistência contra o total do ataque (empate resiste); Meio-Cego vira Cego; Derrubada mín. 1")
    void resistAndDamage() {
        assertThat(EffectRules.resisted(15, 15)).isTrue();
        assertThat(EffectRules.resisted(14, 15)).isFalse();
        assertThat(EffectRules.escalate("MEIO_CEGO", true)).isEqualTo("CEGO");
        assertThat(EffectRules.escalate("MEIO_CEGO", false)).isEqualTo("MEIO_CEGO");
        assertThat(EffectRules.takedownDamage(30)).isEqualTo(1);  // mod −2
        assertThat(EffectRules.takedownDamage(85)).isEqualTo(4);  // mod +2
    }
}
