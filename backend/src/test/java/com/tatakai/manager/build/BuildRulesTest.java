package com.tatakai.manager.build;

import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.BuildGains;
import com.tatakai.manager.entity.Dominance;
import com.tatakai.manager.entity.TraitRarity;
import com.tatakai.manager.service.rules.BuildRules;
import com.tatakai.manager.service.rules.SheetRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BuildRules — Sprint 10 — F2 Construção Inicial")
class BuildRulesTest {

    @ParameterizedTest(name = "d100={0} divino={1} -> {2}")
    @CsvSource({
            "1,false,COMMON", "40,false,COMMON", "41,false,RARE", "70,false,RARE",
            "71,false,EPIC", "90,false,EPIC", "91,false,LEGENDARY", "99,false,LEGENDARY",
            "100,false,LEGENDARY", "100,true,DIVINE", "99,true,LEGENDARY", "40,true,COMMON"
    })
    @DisplayName("RN-TRAIT-01: raridade por d100 (limites 40/41/70/71/90/91/99/100)")
    void rarityFor(int d100, boolean divine, TraitRarity expected) {
        assertThat(BuildRules.rarityFor(d100, divine)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"COMMON,RARE", "RARE,RARE", "EPIC,EPIC", "LEGENDARY,LEGENDARY", "DIVINE,DIVINE"})
    @DisplayName("Prodígio: raridade mínima Raro")
    void promoteForProdigy(TraitRarity in, TraitRarity out) {
        assertThat(BuildRules.promoteForProdigy(in)).isEqualTo(out);
    }

    @ParameterizedTest(name = "d20={0} lowThreshold={1} -> {2} clã(s)")
    @CsvSource({
            "14,false,0", "15,false,0", "17,false,0", "18,false,1", "19,false,1", "20,false,2",
            "14,true,0", "15,true,1", "17,true,1", "18,true,1", "19,true,1", "20,true,2"
    })
    @DisplayName("Teste de Sangue: clãs por d20 (normal 18–19; limiar baixo 15–19; 20 dá 2)")
    void clanSlotsFor(int d20, boolean low, int expected) {
        assertThat(BuildRules.clanSlotsFor(d20, low)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} -> {1} cruza={2}")
    @CsvSource({"3,4,true", "6,7,true", "9,10,true", "4,6,false", "2,3,false", "0,3,false", "3,3,false",
            "2,7,true", "7,9,false"})
    @DisplayName("RN-ATR-06: cruzar Barreira de Inaptidão")
    void crossesBarrier(int before, int after, boolean expected) {
        assertThat(BuildRules.crossesBarrier(before, after)).isEqualTo(expected);
    }

    @Test
    @DisplayName("RN-ATR-01: camadas = (base+clã+traço) * (100+soma%) / 100 com floor, mais pontos livres")
    void layers() {
        Map<Attribute, Integer> base = Map.of(Attribute.STR, 10, Attribute.DEX, 7, Attribute.CON, 9);
        BuildGains clan = BuildGains.builder().attributeFlat(Map.of(Attribute.STR, 2))
                .attributePercent(Map.of(Attribute.STR, 10, Attribute.DEX, 50)).build();
        BuildGains trait = BuildGains.builder().attributeFlat(Map.of(Attribute.STR, 1, Attribute.DEX, 0))
                .attributePercent(Map.of(Attribute.STR, 10)).build();

        var out = BuildRules.layers(base, List.of(clan), List.of(trait), Map.of(Attribute.CON, 2));

        var str = out.get(Attribute.STR);
        assertThat(str.base()).isEqualTo(10);
        assertThat(str.clanFlat()).isEqualTo(2);
        assertThat(str.traitFlat()).isEqualTo(1);
        assertThat(str.percent()).isEqualTo(20);
        assertThat(str.afterPercent()).isEqualTo(15); // 13 * 1.2 = 15.6 -> 15
        assertThat(str.finalValue()).isEqualTo(15);
        assertThat(out.get(Attribute.DEX).afterPercent()).isEqualTo(10); // 7 * 1.5 = 10.5 -> 10
        assertThat(out.get(Attribute.CON).freePoints()).isEqualTo(2);
        assertThat(out.get(Attribute.CON).finalValue()).isEqualTo(11);
        assertThat(out.get(Attribute.TAL).finalValue()).isZero();
        assertThat(out).hasSize(Attribute.values().length);
    }

    @Test
    @DisplayName("RN-ATR-01: sem ganhos nem pontos livres a camada final é a base")
    void layersNoGains() {
        var out = BuildRules.layers(Map.of(Attribute.INT, 8), null, null, null);
        assertThat(out.get(Attribute.INT).finalValue()).isEqualTo(8);
    }

    @ParameterizedTest
    @CsvSource({"RIGHT,false,0,6", "LEFT,false,0,3", "AMBIDEXTROUS,false,0,6", "RIGHT,false,4,10",
            "LEFT,false,2,5", "RIGHT,true,4,0", "LEFT,true,0,0"})
    @DisplayName("RN-ATR-06: pontos livres (6; canhoto -3; + ganhos; aleatório 0)")
    void freePointsAvailable(Dominance d, boolean random, int gained, int expected) {
        List<BuildGains> gains = List.of(BuildGains.builder().freePoints(gained).build());
        assertThat(BuildRules.freePointsAvailable(d, gains, random)).isEqualTo(expected);
    }

    @Test
    @DisplayName("RN-ATR-07: recursos 10+CON, 5+CON/2, 3+modQ(CON) somados aos ganhos")
    void resources() {
        var none = BuildRules.resources(12, List.of());
        assertThat(none.hpMax()).isEqualTo(22);
        assertThat(none.spMax()).isEqualTo(11);
        assertThat(none.kpMax()).isEqualTo(3 + SheetRules.modQ(12));

        var gains = List.of(BuildGains.builder().maxHp(3).maxSp(2).maxKp(1).build(),
                BuildGains.builder().maxHp(-1).build());
        var r = BuildRules.resources(12, gains);
        assertThat(r.hpMax()).isEqualTo(24);
        assertThat(r.spMax()).isEqualTo(13);
        assertThat(r.kpMax()).isEqualTo(3 + SheetRules.modQ(12) + 1);
    }

    @Test
    @DisplayName("RN-REC-01: ajustes de limitador somados por chave")
    void limiterAdjustments() {
        var a = BuildGains.builder().limiters(Map.of("STR", 5, "HP", 10)).build();
        var b = BuildGains.builder().limiters(Map.of("STR", -2, "KP", 1)).build();
        var out = BuildRules.limiterAdjustments(List.of(a, b));
        assertThat(out).containsEntry("STR", 3).containsEntry("HP", 10).containsEntry("KP", 1).hasSize(3);
        assertThat(BuildRules.limiterAdjustments(null)).isEmpty();
    }
}
