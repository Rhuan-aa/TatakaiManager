package com.tatakai.manager.combat;

import com.tatakai.manager.entity.CombatAction;
import com.tatakai.manager.entity.ConditionExpiry;
import com.tatakai.manager.service.rules.CombatRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Regras puras do motor de combate — F6a")
class CombatRulesTest {

    @Test
    @DisplayName("RN-ACAO-04: bônus de iniciativa com/sem arte ativa e penalidade de Desgaste Físico")
    void initiativeBonus() {
        assertThat(CombatRules.initiativeBonus(30, true, 0)).isEqualTo(3);
        assertThat(CombatRules.initiativeBonus(30, false, 0)).isEqualTo(1);
        assertThat(CombatRules.initiativeBonus(30, true, 3)).isEqualTo(2);
        assertThat(CombatRules.initiativeBonus(30, true, 6)).isEqualTo(1);
        assertThat(CombatRules.initiativeBonus(30, true, 9)).isEqualTo(0);
        assertThat(CombatRules.initiativeBonus(30, true, 15)).isEqualTo(0);
        assertThat(CombatRules.initiativeBonus(0, true, 0)).isEqualTo(0);
        assertThat(CombatRules.initiativeBonus(2, true, 0)).isEqualTo(-4);
    }

    @Test
    @DisplayName("Expressão de iniciativa 1d20 com bônus positivo, negativo e zero")
    void expression() {
        assertThat(CombatRules.initiativeExpression(3)).isEqualTo("1d20+3");
        assertThat(CombatRules.initiativeExpression(-2)).isEqualTo("1d20-2");
        assertThat(CombatRules.initiativeExpression(0)).isEqualTo("1d20");
    }

    @Test
    @DisplayName("RN-ACAO-01: recarga de PA = 5 + bônus − perda, mínimo 0")
    void recharge() {
        assertThat(CombatRules.rechargePa(0, 0)).isEqualTo(5);
        assertThat(CombatRules.rechargePa(2, 1)).isEqualTo(6);
        assertThat(CombatRules.rechargePa(0, 9)).isEqualTo(0);
    }

    @Test
    @DisplayName("RN-ACAO-02: custos de PA e SP por ação")
    void costs() {
        assertThat(CombatRules.paCost(CombatAction.MAJOR, false)).isEqualTo(3);
        assertThat(CombatRules.paCost(CombatAction.MAJOR, true)).isEqualTo(4);
        assertThat(CombatRules.paCost(CombatAction.PREPARED, false)).isEqualTo(3);
        assertThat(CombatRules.paCost(CombatAction.BREATHE, false)).isEqualTo(4);
        assertThat(CombatRules.paCost(CombatAction.MINOR, false)).isEqualTo(1);
        assertThat(CombatRules.paCost(CombatAction.MINOR_OFFENSIVE, false)).isEqualTo(2);
        assertThat(CombatRules.paCost(CombatAction.DISENGAGE, false)).isEqualTo(2);
        assertThat(CombatRules.paCost(CombatAction.MOVE, false)).isEqualTo(1);
        assertThat(CombatRules.paCost(CombatAction.STAND_UP, false)).isEqualTo(2);
        assertThat(CombatRules.paCost(CombatAction.SHOVE, false)).isEqualTo(1);
        assertThat(CombatRules.paCost(CombatAction.FREE, false)).isEqualTo(0);
        assertThat(CombatRules.baseChangeCost(false, true)).isEqualTo(1);  // entrar
        assertThat(CombatRules.baseChangeCost(true, true)).isEqualTo(2);   // trocar
        assertThat(CombatRules.baseChangeCost(true, false)).isEqualTo(1);  // sair
        assertThat(CombatRules.spCost(CombatAction.MAJOR, null)).isEqualTo(1);
        assertThat(CombatRules.spCost(CombatAction.MAJOR, 4)).isEqualTo(4);
        assertThat(CombatRules.spCost(CombatAction.MINOR_OFFENSIVE, null)).isEqualTo(1);
        assertThat(CombatRules.spCost(CombatAction.MINOR, 5)).isEqualTo(0);
    }

    @Test
    @DisplayName("RN-ACAO-03: distância de deslocamento cai pela metade, mínimo 1,5")
    void moveDistance() {
        assertThat(CombatRules.moveDistance(1, 30)).isEqualTo(7.0);
        assertThat(CombatRules.moveDistance(2, 30)).isEqualTo(3.5);
        assertThat(CombatRules.moveDistance(3, 30)).isEqualTo(1.75);
        assertThat(CombatRules.moveDistance(4, 30)).isEqualTo(1.5);
        assertThat(CombatRules.moveDistance(1, 0)).isEqualTo(4.0);
    }

    @Test
    @DisplayName("Recuperar Fôlego: 2 + modQ(CON)")
    void breathe() {
        assertThat(CombatRules.breatheRecovery(40)).isEqualTo(6);
        assertThat(CombatRules.breatheRecovery(5)).isEqualTo(2);
    }

    @Test
    @DisplayName("Ordem: iniciativa desc, depois DES desc; empate total é sorteado mas estável em quantidade")
    void sortOrder() {
        record P(String n, int init, int dex) { }
        List<P> l = new ArrayList<>(List.of(new P("a", 10, 20), new P("b", 15, 10), new P("c", 10, 30)));
        CombatRules.sortOrder(l, P::init, P::dex, new Random(1));
        assertThat(l).extracting(P::n).containsExactly("b", "c", "a");

        List<P> tie = new ArrayList<>(List.of(new P("x", 10, 20), new P("y", 10, 20)));
        CombatRules.sortOrder(tie, P::init, P::dex, new Random(2));
        assertThat(tie).extracting(P::n).containsExactlyInAnyOrder("x", "y");
    }

    @Test
    @DisplayName("RN-TURNO-01: decisões de expiração")
    void expiration() {
        assertThat(CombatRules.expiresAtTurnStart(ConditionExpiry.START_OF_TARGET_TURN)).isTrue();
        assertThat(CombatRules.expiresAtTurnStart(ConditionExpiry.END_OF_TARGET_TURN)).isFalse();
        assertThat(CombatRules.expiresAtTurnEnd(ConditionExpiry.END_OF_TARGET_TURN, 3, 3)).isFalse();
        assertThat(CombatRules.expiresAtTurnEnd(ConditionExpiry.END_OF_TARGET_TURN, 2, 3)).isTrue();
        assertThat(CombatRules.expiresAtTurnEnd(ConditionExpiry.MANUAL, 1, 3)).isFalse();
        assertThat(CombatRules.expiresAtRoundEnd(ConditionExpiry.END_OF_ROUND)).isTrue();
        assertThat(CombatRules.tickCounter(2)).isEqualTo(1);
        assertThat(CombatRules.tickCounter(1)).isEqualTo(0);
    }
}
