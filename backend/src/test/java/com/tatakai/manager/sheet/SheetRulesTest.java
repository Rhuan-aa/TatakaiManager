package com.tatakai.manager.sheet;

import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.AttributeBand;
import com.tatakai.manager.entity.BodyType;
import com.tatakai.manager.entity.FatigueBand;
import com.tatakai.manager.entity.InjurySeverity;
import com.tatakai.manager.entity.InjuryType;
import com.tatakai.manager.entity.PermanentInjury;
import com.tatakai.manager.entity.ResourceType;
import com.tatakai.manager.service.rules.SheetRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("SheetRules — Sprint 9 — F1 Ficha")
class SheetRulesTest {

    @ParameterizedTest(name = "mod({0}) = {1}")
    @CsvSource({"1,-4", "3,-4", "4,-2", "6,-2", "7,0", "9,0", "10,1", "19,1", "20,2", "25,2", "100,10"})
    @DisplayName("RN-ATR-02: modificador por faixa de atributo")
    void mod(int v, int expected) {
        assertThat(SheetRules.mod(v)).isEqualTo(expected);
    }

    @Test
    @DisplayName("RN-ATR-02: atributo 0 não tem modificador (null)")
    void modZeroIsNull() {
        assertThat(SheetRules.mod(0)).isNull();
    }

    @ParameterizedTest(name = "modQ({0}) = {1}")
    @CsvSource({"0,0", "1,0", "6,0", "9,0", "10,1", "25,2"})
    @DisplayName("modQ nunca é negativo")
    void modQ(int v, int expected) {
        assertThat(SheetRules.modQ(v)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "half({0}) = {1}")
    @CsvSource({"0,0", "1,0", "3,1", "4,2", "5,2", "-2,-2", "-4,-4"})
    @DisplayName("half: positivos arredondam para baixo, negativos não mudam")
    void half(int m, int expected) {
        assertThat(SheetRules.half(m)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "band({0}) = {1}")
    @CsvSource({"0,COLLAPSE", "1,INEPT", "3,INEPT", "4,DEFICIENT", "6,DEFICIENT", "7,UNTRAINED",
            "9,UNTRAINED", "10,FUNCTIONAL", "50,FUNCTIONAL"})
    @DisplayName("band por faixa de atributo")
    void band(int v, AttributeBand expected) {
        assertThat(SheetRules.band(v)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "limitador {0}/{1} = {2}")
    @CsvSource({
            "STR,ENDOMORPH,40", "CON,ENDOMORPH,50", "DEX,ENDOMORPH,35",
            "STR,MESOMORPH,45", "CON,MESOMORPH,45", "DEX,MESOMORPH,40",
            "STR,ECTOMORPH,40", "DEX,ECTOMORPH,50", "CON,ECTOMORPH,35", "INT,ECTOMORPH,40"})
    @DisplayName("RN-ATR-04: limitador de atributo por biotipo (TAL 0)")
    void attributeLimiterByBodyType(Attribute a, BodyType bt, int expected) {
        assertThat(SheetRules.attributeLimiter(a, 0, bt, 0, 0)).isEqualTo(expected);
    }

    @Test
    @DisplayName("RN-ATR-04: TAL soma tal/2 e a redução de ferimento é subtraída")
    void attributeLimiterTalAndInjury() {
        assertThat(SheetRules.attributeLimiter(Attribute.STR, 11, BodyType.ECTOMORPH, 0, 0)).isEqualTo(45);
        assertThat(SheetRules.attributeLimiter(Attribute.STR, 11, BodyType.ECTOMORPH, 10, 0)).isEqualTo(35);
    }

    @Test
    @DisplayName("RN-ATR-05: TAL não tem limite")
    void talUnlimited() {
        assertThat(SheetRules.attributeLimiter(Attribute.TAL, 5, BodyType.MESOMORPH, 15, 0))
                .isEqualTo(Integer.MAX_VALUE);
    }

    @ParameterizedTest(name = "limitador de status {0}/{1} = {2}")
    @CsvSource({
            "HP,ENDOMORPH,70", "HP,MESOMORPH,65", "HP,ECTOMORPH,60",
            "SP,ENDOMORPH,60", "SP,MESOMORPH,65", "SP,ECTOMORPH,70",
            "KP,ENDOMORPH,8", "KP,MESOMORPH,8", "KP,ECTOMORPH,8"})
    @DisplayName("RN-ATR-06: limitador de HP/SP/KP (TAL 0)")
    void statusLimiter(ResourceType r, BodyType bt, int expected) {
        assertThat(SheetRules.statusLimiter(r, 0, bt, 0)).isEqualTo(expected);
    }

    @Test
    @DisplayName("limitador de status soma tal/2 (exceto KP)")
    void statusLimiterTal() {
        assertThat(SheetRules.statusLimiter(ResourceType.HP, 20, BodyType.ECTOMORPH, 0)).isEqualTo(70);
        assertThat(SheetRules.statusLimiter(ResourceType.KP, 20, BodyType.ECTOMORPH, 0)).isEqualTo(8);
    }

    @Test
    @DisplayName("máximos iniciais de HP, SP e KP a partir de CON")
    void initialMaxes() {
        assertThat(SheetRules.initialHpMax(12)).isEqualTo(22);
        assertThat(SheetRules.initialSpMax(12)).isEqualTo(11);
        assertThat(SheetRules.initialSpMax(13)).isEqualTo(11);
        assertThat(SheetRules.initialKpMax(12)).isEqualTo(4);
        assertThat(SheetRules.initialKpMax(5)).isEqualTo(3);
        assertThat(SheetRules.initialKpMax(0)).isEqualTo(3);
    }

    @Test
    @DisplayName("RN-ATR-08: peso inicial calculado dentro dos limites")
    void initialWeightNormal() {
        // (1,8-0,2)*(50+10/4) = 84
        assertThat(SheetRules.initialWeight(1.8, BodyType.ENDOMORPH, 10)).isCloseTo(84.0, within(0.001));
    }

    @Test
    @DisplayName("RN-ATR-08: peso inicial é limitado pelo índice mínimo e máximo")
    void initialWeightClamp() {
        assertThat(SheetRules.initialWeight(1.8, BodyType.ECTOMORPH, 0)).isCloseTo(3.24 * 15, within(0.001));
        assertThat(SheetRules.initialWeight(1.8, BodyType.MESOMORPH, 999)).isCloseTo(3.24 * 33, within(0.001));
    }

    @ParameterizedTest(name = "weightMod({0}) = {1}")
    @CsvSource({"20,-2", "34.9,-2", "35,-1", "49.9,-1", "50,0", "64.9,0", "65,1", "79.9,1", "80,2",
            "95,3", "110,4", "125,5", "139.9,5", "140,6", "155,7", "170,8"})
    @DisplayName("modificador de peso por faixa")
    void weightMod(double kg, int expected) {
        assertThat(SheetRules.weightMod(kg)).isEqualTo(expected);
    }

    @Test
    @DisplayName("capacidade de carga = 3 x FOR")
    void carry() {
        assertThat(SheetRules.carryKg(12)).isEqualTo(36);
    }

    private static PermanentInjury injury(int reduction) {
        return PermanentInjury.builder().hpMaxReduction(reduction).build();
    }

    @Test
    @DisplayName("HP máximo efetivo subtrai ferimentos e nunca fica abaixo de 1")
    void effectiveHpMax() {
        assertThat(SheetRules.effectiveHpMax(20, List.of())).isEqualTo(20);
        assertThat(SheetRules.effectiveHpMax(20, List.of(injury(5), injury(3)))).isEqualTo(12);
        assertThat(SheetRules.effectiveHpMax(20, List.of(injury(50)))).isEqualTo(1);
    }

    @Test
    @DisplayName("Estado Crítico: HP <= máx/4 ou algum atributo em 0")
    void criticalState() {
        assertThat(SheetRules.criticalState(5, 20, 10, 10)).isTrue();
        assertThat(SheetRules.criticalState(6, 20, 10, 10)).isFalse();
        assertThat(SheetRules.criticalState(20, 20, 10, 0, 10)).isTrue();
        assertThat(SheetRules.criticalState(0, 1, 10)).isTrue();
    }

    @ParameterizedTest(name = "fatigueBand({0}) = {1}")
    @CsvSource({"0,RESTED", "2,RESTED", "3,TIRED", "5,TIRED", "6,DRAINED", "8,DRAINED",
            "9,EXHAUSTED", "11,EXHAUSTED", "12,COLLAPSE", "20,COLLAPSE"})
    @DisplayName("faixa de desgaste nos limites 2/3/5/6/8/9/11/12")
    void fatigueBand(int pts, FatigueBand expected) {
        assertThat(SheetRules.fatigueBand(pts)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} em HP máx 40 = -{1}")
    @CsvSource({"MINOR,4", "MODERATE,10", "SEVERE,18", "LETHAL,40"})
    @DisplayName("redução de HP máximo por gravidade (cortante)")
    void injuryHpReduction(InjurySeverity s, int expected) {
        assertThat(SheetRules.injuryHpReduction(s, InjuryType.CUTTING, 40, null)).isEqualTo(expected);
    }

    @Test
    @DisplayName("contusão reduz floor(dano/2) independentemente da gravidade")
    void bluntReduction() {
        assertThat(SheetRules.injuryHpReduction(InjurySeverity.SEVERE, InjuryType.BLUNT, 40, 7)).isEqualTo(3);
        assertThat(SheetRules.injuryHpReduction(InjurySeverity.MINOR, InjuryType.BLUNT, 40, null)).isZero();
    }

    @ParameterizedTest(name = "{0} reduz limitador em {1}")
    @CsvSource({"MINOR,5", "MODERATE,10", "SEVERE,15", "LETHAL,0"})
    @DisplayName("redução de limitador por gravidade")
    void injuryLimiterReduction(InjurySeverity s, int expected) {
        assertThat(SheetRules.injuryLimiterReduction(s)).isEqualTo(expected);
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("§32: gravidade por %, atributo por região, ferimento ativo por tipo/tratamento")
    void permanentInjuryRules() {
        assertThat(SheetRules.injurySeverity(10, 40)).isEqualTo(com.tatakai.manager.entity.InjurySeverity.MINOR);
        assertThat(SheetRules.injurySeverity(11, 40)).isEqualTo(com.tatakai.manager.entity.InjurySeverity.MODERATE);
        assertThat(SheetRules.injurySeverity(21, 40)).isEqualTo(com.tatakai.manager.entity.InjurySeverity.SEVERE);
        assertThat(SheetRules.injurySeverity(40, 40)).isEqualTo(com.tatakai.manager.entity.InjurySeverity.LETHAL);
        assertThat(SheetRules.injuryAttribute("Braço esquerdo")).isEqualTo(com.tatakai.manager.entity.Attribute.STR);
        assertThat(SheetRules.injuryAttribute("Pé")).isEqualTo(com.tatakai.manager.entity.Attribute.DEX);
        assertThat(SheetRules.injuryAttribute("Fígado")).isEqualTo(com.tatakai.manager.entity.Attribute.CON);
        assertThat(SheetRules.injuryAttribute("Nariz")).isEqualTo(com.tatakai.manager.entity.Attribute.CHA);
        assertThat(SheetRules.injuryAttribute("Queixo")).isNull();
        assertThat(SheetRules.injuryLimb("perna direita")).isTrue();
        var cut = com.tatakai.manager.entity.PermanentInjury.builder().type(com.tatakai.manager.entity.InjuryType.CUTTING)
                .hpMaxReduction(4).treated(true).build();
        var blunt = com.tatakai.manager.entity.PermanentInjury.builder().type(com.tatakai.manager.entity.InjuryType.BLUNT)
                .hpMaxReduction(6).treated(true).build();
        assertThat(SheetRules.injuryHp(java.util.List.of(cut, blunt))).isEqualTo(4); // tratado só some se não for cortante
        assertThat(SheetRules.effectiveHpMax(30, java.util.List.of(cut, blunt))).isEqualTo(26);
    }
}
