package com.tatakai.manager.progression;

import com.tatakai.manager.entity.FightClassification;
import com.tatakai.manager.entity.Graduation;
import com.tatakai.manager.entity.KeikenRank;
import com.tatakai.manager.entity.Mastery;
import com.tatakai.manager.service.rules.ProgressionRules;
import com.tatakai.manager.service.rules.ProgressionRules.Requirement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressionRulesTest {

    @Test
    @DisplayName("F4b — RN-PROG-08: patente pelo EL total, próxima patente e vagas de Instinto")
    void keiken() {
        assertThat(ProgressionRules.keiken(0)).isEqualTo(KeikenRank.NOVICE);
        assertThat(ProgressionRules.keiken(4)).isEqualTo(KeikenRank.NOVICE);
        assertThat(ProgressionRules.keiken(5)).isEqualTo(KeikenRank.SEASONED);
        assertThat(ProgressionRules.keiken(119)).isEqualTo(KeikenRank.MYTHIC);
        assertThat(ProgressionRules.keiken(500)).isEqualTo(KeikenRank.DRAGON_GOD);
        assertThat(ProgressionRules.nextKeikenAt(6)).isEqualTo(15);
        assertThat(ProgressionRules.nextKeikenAt(120)).isNull();
        assertThat(ProgressionRules.instinctSlots(0)).isZero();
        assertThat(ProgressionRules.instinctSlots(30)).isEqualTo(3);
    }

    @Test
    @DisplayName("F4b — RN-PROG-07: EL Fácil 1 (1×/dia), Real 2, Letal 3; sparring até o teto acumulado da patente")
    void fightEl() {
        assertThat(ProgressionRules.fightEl(FightClassification.EASY, false, 0, 0)).isEqualTo(1);
        assertThat(ProgressionRules.fightEl(FightClassification.EASY, true, 0, 0)).isZero();
        assertThat(ProgressionRules.fightEl(FightClassification.REAL, true, 0, 0)).isEqualTo(2);
        assertThat(ProgressionRules.fightEl(FightClassification.LETHAL, false, 0, 0)).isEqualTo(3);
        assertThat(ProgressionRules.fightEl(FightClassification.SPARRING, false, 2, 2)).isEqualTo(1);
        assertThat(ProgressionRules.fightEl(FightClassification.SPARRING, false, 3, 3)).isZero();
        // Calejado: teto 5 acumulado
        assertThat(ProgressionRules.fightEl(FightClassification.SPARRING, false, 4, 8)).isEqualTo(1);
        assertThat(ProgressionRules.fightEl(FightClassification.SPARRING, false, 5, 8)).isZero();
    }

    @Test
    @DisplayName("F4b — PS = dificuldade 1/2/3 + min(Marcos, 4) + floor(modQ(TAL)/2); sparring 0")
    void fightPs() {
        int halfTal = com.tatakai.manager.service.rules.SheetRules.modQ(25) / 2;
        assertThat(ProgressionRules.fightPs(FightClassification.EASY, 0, 25)).isEqualTo(1 + halfTal);
        assertThat(ProgressionRules.fightPs(FightClassification.LETHAL, 6, 25)).isEqualTo(3 + 4 + halfTal);
        assertThat(ProgressionRules.fightPs(FightClassification.SPARRING, 3, 25)).isZero();
    }

    @Test
    @DisplayName("F4b — distribuição de PS: alvos válidos, INT/CAR máx. 2, TAL/PQ não recebem, soma ≤ pendentes")
    void allocation() {
        assertThat(ProgressionRules.validateAllocation(Map.of("STR", 2, "HP", 1), 3)).isNull();
        assertThat(ProgressionRules.validateAllocation(Map.of("STR", 4), 3)).contains("insuficientes");
        assertThat(ProgressionRules.validateAllocation(Map.of("INT", 3), 5)).contains("máximo 2");
        assertThat(ProgressionRules.validateAllocation(Map.of("TAL", 1), 5)).contains("inválido");
        assertThat(ProgressionRules.validateAllocation(Map.of("KP", 1), 5)).contains("inválido");
        assertThat(ProgressionRules.validateAllocation(Map.of(), 5)).isNotNull();
    }

    @Test
    @DisplayName("F4b — RN-PROG-09: custo 8/12 (Geral 9/18) e Keiken exigida pela ordem da arte Mestre")
    void graduationCostAndKeiken() {
        assertThat(ProgressionRules.graduationCost(Graduation.INTERMEDIATE, false)).isEqualTo(8);
        assertThat(ProgressionRules.graduationCost(Graduation.MASTER, true)).isEqualTo(18);
        assertThat(ProgressionRules.requiredKeiken(Graduation.INTERMEDIATE, 3)).isEqualTo(KeikenRank.SEASONED);
        assertThat(ProgressionRules.requiredKeiken(Graduation.MASTER, 0)).isEqualTo(KeikenRank.VETERAN);
        assertThat(ProgressionRules.requiredKeiken(Graduation.MASTER, 1)).isEqualTo(KeikenRank.HISTORIC);
        assertThat(ProgressionRules.requiredKeiken(Graduation.MASTER, 9)).isEqualTo(KeikenRank.DRAGON_GOD);
        assertThat(ProgressionRules.nextGraduation(Graduation.MASTER)).isNull();
    }

    @Test
    @DisplayName("F4b — RN-PROG-09: requisitos em paralelo (completude, especialização, Keiken, pontos, duelo)")
    void requirements() {
        List<Mastery> ready = List.of(Mastery.IMPROVED, Mastery.IMPROVED, Mastery.SPECIALIZED);
        assertThat(ProgressionRules.graduationRequirements(Graduation.INTERMEDIATE, ready, 5, 0, 8, false, false))
                .allMatch(Requirement::met).hasSize(4);

        List<Requirement> notYet = ProgressionRules.graduationRequirements(Graduation.MASTER,
                List.of(Mastery.IMPROVED, Mastery.STANDARD), 10, 0, 3, false, false);
        assertThat(notYet).extracting(Requirement::key)
                .containsExactly("COMPLETENESS", "SPECIALIZATION", "KEIKEN", "POINTS", "DUEL");
        assertThat(notYet).noneMatch(Requirement::met);
    }
}
