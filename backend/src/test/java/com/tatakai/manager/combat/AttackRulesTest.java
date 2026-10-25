package com.tatakai.manager.combat;

import com.tatakai.manager.entity.AttackActionKind;
import com.tatakai.manager.entity.AttackOutcome;
import com.tatakai.manager.entity.ReactionType;
import com.tatakai.manager.service.rules.AttackRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AttackRules — F6b regras puras")
class AttackRulesTest {

    private static AttackRules.Result r(ReactionType rt, int atk, int rea) {
        return AttackRules.resolve(rt, atk, false, false, rea, false, false);
    }

    @Test
    @DisplayName("Custos de PA: Maior 3, Lenta 4, Menor Ofensiva 2")
    void costs() {
        assertThat(AttackRules.paCost(AttackActionKind.MAJOR, false)).isEqualTo(3);
        assertThat(AttackRules.paCost(AttackActionKind.MAJOR, true)).isEqualTo(4);
        assertThat(AttackRules.paCost(AttackActionKind.MINOR_OFFENSIVE, false)).isEqualTo(2);
    }

    @Test
    @DisplayName("Esquiva: empate falha, maior vence")
    void dodge() {
        assertThat(r(ReactionType.DODGE, 15, 15).outcome()).isEqualTo(AttackOutcome.HIT);
        assertThat(r(ReactionType.DODGE, 15, 16).outcome()).isEqualTo(AttackOutcome.DODGED);
    }

    @Test
    @DisplayName("Defender: faixas 50/75/100 e 25% na falha")
    void defend() {
        assertThat(r(ReactionType.DEFEND, 15, 15).mitigationPct()).isEqualTo(50);
        assertThat(r(ReactionType.DEFEND, 15, 18).mitigationPct()).isEqualTo(50);
        assertThat(r(ReactionType.DEFEND, 15, 19).mitigationPct()).isEqualTo(75);
        assertThat(r(ReactionType.DEFEND, 15, 22).mitigationPct()).isEqualTo(75);
        assertThat(r(ReactionType.DEFEND, 15, 23).mitigationPct()).isEqualTo(100);
        assertThat(r(ReactionType.DEFEND, 15, 14)).isEqualTo(new AttackRules.Result(AttackOutcome.HIT, 25, false));
        assertThat(AttackRules.resolve(ReactionType.DEFEND, 15, false, false, 14, false, true).mitigationPct()).isZero();
    }

    @Test
    @DisplayName("Contra: > vence; >= 1,5x não recebe dano")
    void counter() {
        assertThat(r(ReactionType.COUNTER, 10, 10).outcome()).isEqualTo(AttackOutcome.HIT);
        assertThat(r(ReactionType.COUNTER, 10, 11)).isEqualTo(new AttackRules.Result(AttackOutcome.COUNTERED, 0, true));
        assertThat(r(ReactionType.COUNTER, 10, 15)).isEqualTo(new AttackRules.Result(AttackOutcome.COUNTERED, 100, true));
    }

    @Test
    @DisplayName("Crítico vs crítico: Defender fica 50%, Esquiva/Contra falham; falha crítica = MISS")
    void crits() {
        assertThat(AttackRules.resolve(ReactionType.DEFEND, 30, true, false, 40, true, false).mitigationPct()).isEqualTo(50);
        assertThat(AttackRules.resolve(ReactionType.DODGE, 30, true, false, 40, true, false).outcome()).isEqualTo(AttackOutcome.HIT);
        assertThat(AttackRules.resolve(ReactionType.COUNTER, 30, true, false, 40, true, false).outcome()).isEqualTo(AttackOutcome.HIT);
        assertThat(AttackRules.resolve(ReactionType.NONE, 3, false, true, 0, false, false).outcome()).isEqualTo(AttackOutcome.MISS);
    }

    @Test
    @DisplayName("Dano: arredonda para baixo, mínimo 1, 100% zera")
    void damage() {
        assertThat(AttackRules.damage(7, 50)).isEqualTo(3);
        assertThat(AttackRules.damage(1, 75)).isEqualTo(1);
        assertThat(AttackRules.damage(0, 0)).isEqualTo(1);
        assertThat(AttackRules.damage(9, 100)).isZero();
        assertThat(AttackRules.groupPenalty(3)).isEqualTo(-2);
        assertThat(AttackRules.timingBonus(1)).isZero();
        assertThat(AttackRules.timingBonus(3)).isEqualTo(2);
    }
}
