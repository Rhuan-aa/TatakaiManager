package com.tatakai.manager.combat;

import com.tatakai.manager.entity.AssemblyItem;
import com.tatakai.manager.entity.MartialType;
import com.tatakai.manager.entity.ReactionType;
import com.tatakai.manager.service.rules.BaseRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BaseRules — números da base ativa pelo rótulo (§31)")
class BaseRulesTest {

    private static AssemblyItem it(String module, String label, int qty) {
        AssemblyItem i = new AssemblyItem();
        i.setModule(module);
        i.setLabel(label);
        i.setQty(qty);
        return i;
    }

    @Test
    @DisplayName("valor e escopo do rótulo; quantidade (custo) é ignorada")
    void parse() {
        assertThat(BaseRules.value("Penalidade −2 em Esquiva")).isEqualTo(-2);
        assertThat(BaseRules.value("Passada +1,5 m")).isEqualTo(1.5);
        assertThat(BaseRules.value("Calejamento 3 contundente")).isEqualTo(3);
        assertThat(BaseRules.scope("Guarda +1 em Contra-Ataque")).isEqualTo(BaseRules.Scope.COUNTER);
        assertThat(BaseRules.scope("Precisão +1 em Derrubada")).isEqualTo(BaseRules.Scope.TAKEDOWN);
        assertThat(BaseRules.scope("Penalidade −3 fora de chutes e joelhadas")).isEqualTo(BaseRules.Scope.OTHER);
    }

    @Test
    @DisplayName("Fudo Dachi: +3 Defender, −2 Esquiva, Enraizado +2, Pesada −1,5 m")
    void fudoDachi() {
        BaseRules.Profile p = BaseRules.profile("Fudo Dachi", List.of(
                it("Guarda", "Guarda +3 em Defender", 3), it("Enraizado", "Enraizado +2", 2),
                it("Penalidade", "Penalidade −2 em Esquiva", 2), it("Pesada", "Pesada −1,5 m", 1)), false);
        assertThat(p.reaction(ReactionType.DEFEND)).isEqualTo(3);
        assertThat(p.reaction(ReactionType.DODGE)).isEqualTo(-2);
        assertThat(p.reaction(ReactionType.COUNTER)).isZero();
        assertThat(p.rooted()).isEqualTo(2);
        assertThat(p.moveMeters()).isEqualTo(-1.5);
    }

    @Test
    @DisplayName("Precisão/Força do Golpe por tipo; não afim com qty de custo vale o rótulo; Esboço divide")
    void attackNumbers() {
        BaseRules.Profile p = BaseRules.profile("Powerbomb", List.of(
                it("Força do Golpe", "Força do Golpe +2 em Derrubada", 2), it("Precisão", "Precisão +1 em Derrubada", 2),
                it("Penalidade", "Penalidade −2 em Trocação", 2), it("Calejamento", "Calejamento 3 contundente", 3),
                it("Fraqueza", "Fraqueza contra Derrubada", 1), it("Calma", "Calma +2", 1)), false);
        assertThat(p.precision(MartialType.TAKEDOWN)).isEqualTo(1);
        assertThat(p.precision(MartialType.STRIKING)).isZero();
        assertThat(p.strike(MartialType.TAKEDOWN)).isEqualTo(2);
        assertThat(p.attackPenalty(MartialType.STRIKING)).isEqualTo(-2);
        assertThat(p.attackPenalty(MartialType.TAKEDOWN)).isZero();
        assertThat(p.calejo()).isEqualTo(3);
        assertThat(p.calm()).isEqualTo(2);
        assertThat(p.weakAgainst(MartialType.TAKEDOWN)).isTrue();
        assertThat(p.weakAgainst(MartialType.STRIKING)).isFalse();
        BaseRules.Profile sketch = BaseRules.profile("x", List.of(it("Precisão", "Precisão +3", 3),
                it("Calejamento", "Calejamento 3", 6)), true);
        assertThat(sketch.precision(null)).isEqualTo(1);
        assertThat(sketch.calejo()).isEqualTo(1);
    }

    @Test
    @DisplayName("Esboço: metade de todos os bônus (para baixo) e reduções inteiras; Resiliência")
    void sketchAndResilience() {
        BaseRules.Profile p = BaseRules.profile("x", List.of(
                it("Guarda", "Guarda +3 em Defender", 3), it("Penalidade", "Penalidade −2 em Esquiva", 2),
                it("Enraizado", "Enraizado +4", 2), it("Resiliência", "Resiliência +2", 2)), true);
        assertThat(p.reaction(ReactionType.DEFEND)).isEqualTo(1);  // 3 -> 1
        assertThat(p.reaction(ReactionType.DODGE)).isEqualTo(-2);  // redução inteira
        assertThat(p.rooted()).isEqualTo(2);
        assertThat(p.resilience()).isEqualTo(1);
    }

    @Test
    @DisplayName("Fôlego, Economia (por tipo), Absorção Contínua, Troca Fluida, Punição e Avanço Pesado")
    void otherModules() {
        BaseRules.Profile p = BaseRules.profile("x", List.of(
                it("Fôlego", "Fôlego", 3), it("Economia", "Economia em Trocação", 2), it("Absorção Contínua", "Absorção Contínua", 3),
                it("Troca Fluida", "Troca Fluida", 2), it("Punição", "Punição contra Derrubada", 1), it("Avanço Pesado", "Avanço Pesado", 3)), false);
        assertThat(p.breath()).isTrue();
        assertThat(p.economy(MartialType.STRIKING)).isTrue();
        assertThat(p.economy(MartialType.TAKEDOWN)).isFalse();
        assertThat(p.continuousAbsorb()).isTrue();
        assertThat(p.fluidSwitch()).isTrue();
        assertThat(p.punishes(MartialType.TAKEDOWN)).isTrue();
        assertThat(p.punishes(MartialType.STRIKING)).isFalse();
        assertThat(p.heavyAdvance()).isTrue();
        assertThat(BaseRules.profile("y", List.of(it("Economia", "Economia", 2)), false).economy(MartialType.SWEEP)).isTrue();
    }
}
