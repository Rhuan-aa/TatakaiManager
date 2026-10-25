package com.tatakai.manager.combat;

import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.ConditionExpiry;
import com.tatakai.manager.service.rules.EffectRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EffectRules — tabela de Efeitos da Criação Marcial (§30)")
class EffectRulesTest {

    @Test
    @DisplayName("atributos de resistência e durações do livro; efeito sem regra fica manual")
    void mapping() {
        EffectRules.Effect derrubar = EffectRules.of("Derrubar").orElseThrow();
        assertThat(derrubar.kind()).isEqualTo(EffectRules.Kind.TAKEDOWN);
        assertThat(derrubar.resist()).isEqualTo(Attribute.STR);
        assertThat(derrubar.alt()).isEqualTo(Attribute.DEX); // Força ou Destreza
        assertThat(EffectRules.of("Quebrar Base").get().resist()).isEqualTo(Attribute.DEX);
        assertThat(EffectRules.of("Expor").get().resist()).isEqualTo(Attribute.MEN);
        assertThat(EffectRules.of("Expor").get().expiry()).isEqualTo(ConditionExpiry.END_OF_TARGET_TURN);
        assertThat(EffectRules.of("Roubar Tempo").get().resist()).isEqualTo(Attribute.MEN);
        assertThat(EffectRules.of("Paralisar").get().expiry()).isEqualTo(ConditionExpiry.END_OF_TARGET_TURN);
        assertThat(EffectRules.of("Abrir Ferida").get().resist()).isEqualTo(Attribute.CON);
        assertThat(EffectRules.of("Aleijar").get().condition()).isEqualTo("ALEIJADO");
        assertThat(EffectRules.of("Marcar").get().resist()).isEqualTo(Attribute.MEN);
        assertThat(EffectRules.of("Interromper")).isEmpty();
        assertThat(EffectRules.of(null)).isEmpty();
    }

    @Test
    @DisplayName("Prender: Golpe agarra, Controle imobiliza; Roubar Tempo no máximo 2 PA; Pressão Fixa troca o ND")
    void details() {
        EffectRules.Effect prender = EffectRules.of("Prender").orElseThrow();
        assertThat(EffectRules.condition(prender, false)).isEqualTo("AGARRADO");
        assertThat(EffectRules.condition(prender, true)).isEqualTo("IMOBILIZADO");
        assertThat(EffectRules.stolenPa(1)).isEqualTo(1);
        assertThat(EffectRules.stolenPa(4)).isEqualTo(2);
        assertThat(EffectRules.nd(17, false, 3)).isEqualTo(17);
        assertThat(EffectRules.nd(17, true, 3)).isEqualTo(13); // 10 + mod. de FOR
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
    @DisplayName("resistência (empate resiste); Meio-Cego vira Cego; Derrubada mín. 1")
    void resistAndDamage() {
        assertThat(EffectRules.resisted(15, 15)).isTrue();
        assertThat(EffectRules.resisted(14, 15)).isFalse();
        assertThat(EffectRules.escalate("MEIO_CEGO", true)).isEqualTo("CEGO");
        assertThat(EffectRules.escalate("MEIO_CEGO", false)).isEqualTo("MEIO_CEGO");
        assertThat(EffectRules.takedownDamage(30)).isEqualTo(1);
        assertThat(EffectRules.takedownDamage(85)).isEqualTo(4);
    }
}
