package com.tatakai.manager.combat;

import com.tatakai.manager.entity.BasicAttack;
import com.tatakai.manager.entity.CharacterArt;
import com.tatakai.manager.entity.Dominance;
import com.tatakai.manager.entity.Foot;
import com.tatakai.manager.entity.Graduation;
import com.tatakai.manager.entity.Guard;
import com.tatakai.manager.entity.MartialType;
import com.tatakai.manager.entity.ReactionType;
import com.tatakai.manager.entity.StrikeSide;
import com.tatakai.manager.service.rules.AttackRules;
import com.tatakai.manager.service.rules.MartialRules;
import com.tatakai.manager.service.rules.MartialRules.Family;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.tatakai.manager.entity.MartialType.*;
import static org.assertj.core.api.Assertions.assertThat;

/** F6d: regras puras de interação marcial, especialização, guarda e Calejamento. */
class MartialRulesTest {

    @Test
    @DisplayName("Tipo marcial: normaliza os textos reais do catálogo")
    void types() {
        assertThat(MartialRules.type("Trocação")).isEqualTo(STRIKING);
        assertThat(MartialRules.type("Derrubada")).isEqualTo(TAKEDOWN);
        assertThat(MartialRules.type("Raspagem")).isEqualTo(SWEEP);
        assertThat(MartialRules.type("Combate Desarmado - Geral")).isEqualTo(GENERAL);
        assertThat(MartialRules.type(null)).isNull();
        assertThat(MartialRules.type("Lâmina")).isNull();
    }

    @Test
    @DisplayName("Matriz: Trocação > Derrubada > Raspagem > Trocação; GENERAL vence os três e não perde")
    void matrix() {
        assertThat(MartialRules.winner(STRIKING, TAKEDOWN)).isEqualTo(1);
        assertThat(MartialRules.winner(TAKEDOWN, SWEEP)).isEqualTo(1);
        assertThat(MartialRules.winner(SWEEP, STRIKING)).isEqualTo(1);
        assertThat(MartialRules.winner(TAKEDOWN, STRIKING)).isEqualTo(-1);
        assertThat(MartialRules.winner(STRIKING, STRIKING)).isZero();
        assertThat(MartialRules.winner(GENERAL, SWEEP)).isEqualTo(1);
        assertThat(MartialRules.winner(STRIKING, GENERAL)).isEqualTo(-1);
        assertThat(MartialRules.winner(GENERAL, GENERAL)).isZero();
        assertThat(MartialRules.winner(null, STRIKING)).isZero();
        assertThat(MartialRules.winner(STRIKING, null)).isZero();
    }

    @Test
    @DisplayName("Coringa do perdedor anula a vantagem do oponente; Coringa do vencedor não faz nada")
    void joker() {
        assertThat(MartialRules.interaction(STRIKING, TAKEDOWN, false, true)).isZero();
        assertThat(MartialRules.interaction(STRIKING, TAKEDOWN, true, false)).isEqualTo(1);
        assertThat(MartialRules.interaction(TAKEDOWN, STRIKING, true, false)).isZero();
        assertThat(MartialRules.interaction(TAKEDOWN, STRIKING, false, true)).isEqualTo(-1);
        assertThat(MartialRules.interaction(GENERAL, STRIKING, false, true)).isZero();
    }

    @Test
    @DisplayName("Especialização de ataque: strings reais do catálogo (plural, acento, Geral)")
    void attackSpecs() {
        assertThat(MartialRules.attackSpecs(List.of("Socos", "Chute", "Cotovelada")))
                .containsExactlyInAnyOrder(Family.SOCO, Family.CHUTE, Family.COTOVELADA);
        assertThat(MartialRules.attackSpecs(List.of("Agarrões (Constrições)"))).containsExactly(Family.AGARRAO);
        assertThat(MartialRules.attackSpecs(List.of("Projeções", "Arremessos", "Clinch", "Constrições", "Agarrões")))
                .containsExactly(Family.AGARRAO);
        assertThat(MartialRules.attackSpecs(List.of("Cabeçadas", "Joelhada")))
                .containsExactlyInAnyOrder(Family.CABECADA, Family.JOELHADA);
        assertThat(MartialRules.attackSpecs(List.of("Geral - Ataques Básicos"))).containsExactlyInAnyOrder(Family.values());
        assertThat(MartialRules.attackSpecs(null)).isEmpty();
    }

    @Test
    @DisplayName("Especialização de reação: Defesa, Desvio/Esquiva, Contra-ataque, Geral")
    void reactionSpecs() {
        assertThat(MartialRules.reactionSpecs(List.of("Defesa", "Contra-Ataque")))
                .containsExactlyInAnyOrder(ReactionType.DEFEND, ReactionType.COUNTER);
        assertThat(MartialRules.reactionSpecs(List.of("Desvio"))).containsExactly(ReactionType.DODGE);
        assertThat(MartialRules.reactionSpecs(List.of("Esquiva", "Contra-ataque")))
                .containsExactlyInAnyOrder(ReactionType.DODGE, ReactionType.COUNTER);
        assertThat(MartialRules.reactionSpecs(List.of("Geral")))
                .containsExactlyInAnyOrder(ReactionType.DEFEND, ReactionType.DODGE, ReactionType.COUNTER);
    }

    @Test
    @DisplayName("Família do ataque básico e escopo da guarda (lado único)")
    void families() {
        assertThat(MartialRules.family(BasicAttack.CHUTE)).isEqualTo(Family.CHUTE);
        assertThat(MartialRules.family(BasicAttack.AGARRAO)).isEqualTo(Family.AGARRAO);
        assertThat(MartialRules.oneSided(BasicAttack.SOCO)).isTrue();
        assertThat(MartialRules.oneSided(BasicAttack.CABECADA)).isFalse();
        assertThat(MartialRules.oneSided(BasicAttack.AGARRAO)).isFalse();
    }

    @Test
    @DisplayName("Tabela de guarda: frente +1; trás Invertida −1 (ambidestro sem o −1) e +1d2 de dano; Frontal nada")
    void guardTable() {
        assertThat(MartialRules.guardHit(Guard.NATURAL, StrikeSide.FRONT, false)).isEqualTo(1);
        assertThat(MartialRules.guardHit(Guard.INVERTED, StrikeSide.FRONT, false)).isEqualTo(1);
        assertThat(MartialRules.guardHit(Guard.NATURAL, StrikeSide.BACK, false)).isZero();
        assertThat(MartialRules.guardHit(Guard.INVERTED, StrikeSide.BACK, false)).isEqualTo(-1);
        assertThat(MartialRules.guardHit(Guard.INVERTED, StrikeSide.BACK, true)).isZero();
        assertThat(MartialRules.guardHit(Guard.FRONTAL, StrikeSide.FRONT, false)).isZero();
        assertThat(MartialRules.guardHit(Guard.NATURAL, null, false)).isZero();
        assertThat(MartialRules.guardBackDamage(Guard.NATURAL, StrikeSide.BACK)).isTrue();
        assertThat(MartialRules.guardBackDamage(Guard.INVERTED, StrikeSide.BACK)).isTrue();
        assertThat(MartialRules.guardBackDamage(Guard.FRONTAL, StrikeSide.BACK)).isFalse();
        assertThat(MartialRules.guardBackDamage(Guard.NATURAL, StrikeSide.FRONT)).isFalse();
    }

    @Test
    @DisplayName("Derrubado trata a guarda como Frontal")
    void downedIsFrontal() {
        assertThat(MartialRules.effectiveGuard(Guard.INVERTED, true)).isEqualTo(Guard.FRONTAL);
        assertThat(MartialRules.effectiveGuard(null, false)).isEqualTo(Guard.NATURAL);
    }

    @Test
    @DisplayName("Pé à frente: destro natural = esquerdo; invertida troca; canhoto natural = direito; ambidestro conta destro")
    void leadFoot() {
        assertThat(MartialRules.leadFoot(Dominance.RIGHT, Guard.NATURAL)).isEqualTo(Foot.LEFT);
        assertThat(MartialRules.leadFoot(Dominance.RIGHT, Guard.INVERTED)).isEqualTo(Foot.RIGHT);
        assertThat(MartialRules.leadFoot(Dominance.LEFT, Guard.NATURAL)).isEqualTo(Foot.RIGHT);
        assertThat(MartialRules.leadFoot(Dominance.LEFT, Guard.INVERTED)).isEqualTo(Foot.LEFT);
        assertThat(MartialRules.leadFoot(Dominance.AMBIDEXTROUS, Guard.NATURAL)).isEqualTo(Foot.LEFT);
        assertThat(MartialRules.leadFoot(null, Guard.NATURAL)).isEqualTo(Foot.LEFT);
    }

    @Test
    @DisplayName("Incomum: pé direito vs esquerdo; anulado por Frontal, Leitura do atacante e atacante ambidestro")
    void incomum() {
        assertThat(MartialRules.incomum(Foot.RIGHT, Foot.LEFT, Guard.NATURAL, Guard.NATURAL, false, false)).isTrue();
        assertThat(MartialRules.incomum(Foot.LEFT, Foot.LEFT, Guard.NATURAL, Guard.NATURAL, false, false)).isFalse();
        assertThat(MartialRules.incomum(Foot.RIGHT, Foot.RIGHT, Guard.NATURAL, Guard.NATURAL, false, false)).isFalse();
        assertThat(MartialRules.incomum(Foot.RIGHT, Foot.LEFT, Guard.FRONTAL, Guard.NATURAL, false, false)).isFalse();
        assertThat(MartialRules.incomum(Foot.RIGHT, Foot.LEFT, Guard.NATURAL, Guard.FRONTAL, false, false)).isFalse();
        assertThat(MartialRules.incomum(Foot.RIGHT, Foot.LEFT, Guard.NATURAL, Guard.NATURAL, false, true)).isFalse();
        assertThat(MartialRules.incomum(Foot.RIGHT, Foot.LEFT, Guard.NATURAL, Guard.NATURAL, true, false)).isFalse();
    }

    @Test
    @DisplayName("Arte principal padrão: maior graduação, empate pela mais antiga")
    void defaultMain() {
        CharacterArt old = CharacterArt.builder().graduation(Graduation.BEGINNER).createdAt(Instant.parse("2026-01-01T00:00:00Z")).build();
        CharacterArt newer = CharacterArt.builder().graduation(Graduation.BEGINNER).createdAt(Instant.parse("2026-02-01T00:00:00Z")).build();
        CharacterArt high = CharacterArt.builder().graduation(Graduation.INTERMEDIATE).createdAt(Instant.parse("2026-03-01T00:00:00Z")).build();
        assertThat(MartialRules.defaultMainArt(List.of(newer, old))).isSameAs(old);
        assertThat(MartialRules.defaultMainArt(List.of(newer, old, high))).isSameAs(high);
        assertThat(MartialRules.defaultMainArt(List.of())).isNull();
    }

    @Test
    @DisplayName("Calejamento (valor em BaseRules): entra depois da % e antes do mínimo 1")
    void calejo() {
        // 20 de dano, defesa 50%: 10 - 6 = 4
        assertThat(AttackRules.damage(20, 50, 6)).isEqualTo(4);
        // a redução nunca leva abaixo de 1 se o golpe conectou
        assertThat(AttackRules.damage(5, 0, 6)).isEqualTo(1);
        // mitigação 100% continua zerando
        assertThat(AttackRules.damage(20, 100, 6)).isZero();
        // sem Calejamento, comportamento antigo
        assertThat(AttackRules.damage(10, 25)).isEqualTo(7);
    }

    @Test
    @DisplayName("Rótulos dos tipos para o breakdown")
    void labels() {
        assertThat(MartialType.STRIKING.label()).isEqualTo("Trocação");
        assertThat(MartialType.GENERAL.label()).isEqualTo("Geral");
    }
}
