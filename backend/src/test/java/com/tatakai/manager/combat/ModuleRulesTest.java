package com.tatakai.manager.combat;

import com.tatakai.manager.catalog.entity.AssemblyItem;
import com.tatakai.manager.catalog.entity.AssemblyItemKind;
import com.tatakai.manager.catalog.service.rules.ModuleRules;
import com.tatakai.manager.combat.entity.Floor;
import com.tatakai.manager.sheet.entity.Attribute;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleRulesTest {

    private static AssemblyItem it(String module, String label, int cost) {
        AssemblyItem i = new AssemblyItem();
        i.setModule(module);
        i.setLabel(label);
        i.setCost(cost);
        i.setKind(cost < 0 ? AssemblyItemKind.LIMITATION : AssemblyItemKind.MODULE);
        return i;
    }

    @Test
    @DisplayName("§41: Golpe no Fôlego, Dano Persistente, Massa e Atributo Alternativo")
    void potency() {
        assertThat(ModuleRules.breath(List.of(it("Fôlego (só no SP)", null, 0)))).isEqualTo(ModuleRules.Breath.SP_ONLY);
        assertThat(ModuleRules.breath(List.of(it("Fôlego", null, 2)))).isEqualTo(ModuleRules.Breath.BOTH);
        assertThat(ModuleRules.breath(List.of())).isEqualTo(ModuleRules.Breath.NONE);
        assertThat(ModuleRules.persistentTurns(List.of(it("Dano Persistente", null, 3)))).isEqualTo(1);
        assertThat(ModuleRules.persistentTurns(List.of(it("Dano Persistente", null, 5)))).isEqualTo(3);
        assertThat(ModuleRules.massDamage(List.of(it("Massa", "Massa", 2)), 3)).isEqualTo(3);
        assertThat(ModuleRules.massHit(List.of(it("Massa", "Massa no acerto", 2)), 7)).isEqualTo(3);
        assertThat(ModuleRules.massDamage(List.of(it("Massa", "Massa no acerto", 2)), 3)).isZero();
        assertThat(ModuleRules.altAttribute(List.of(it("Atributo Alternativo", "Inteligência no teste", 1))))
                .isEqualTo(new ModuleRules.AltAttribute(Attribute.INT, true));
        assertThat(ModuleRules.altAttribute(List.of(it("Atributo Alternativo", "Destreza", 1))))
                .isEqualTo(new ModuleRules.AltAttribute(Attribute.DEX, false));
    }

    @Test
    @DisplayName("§41: Ímpeto, alvos, Tardia, Previsível, Risco, Exige Base, Encadear e chão")
    void execution() {
        assertThat(ModuleRules.impetus(1.4)).isZero();
        assertThat(ModuleRules.impetus(3.0)).isEqualTo(4);
        assertThat(ModuleRules.impetus(20.0)).isEqualTo(6);
        assertThat(ModuleRules.maxTargets(List.of())).isEqualTo(1);
        assertThat(ModuleRules.maxTargets(List.of(it("Varredura", null, 3)))).isEqualTo(2);
        assertThat(ModuleRules.maxTargets(List.of(it("Círculo", null, 4)))).isEqualTo(Integer.MAX_VALUE);
        assertThat(ModuleRules.minRound(List.of())).isEqualTo(1);
        assertThat(ModuleRules.minRound(List.of(it("Tardia", null, -1)))).isEqualTo(3);
        assertThat(ModuleRules.minRound(List.of(it("Tardia (3)", null, -3)))).isEqualTo(7);
        assertThat(ModuleRules.predictable(List.of(it("Previsível", null, -1)), 2)).isEqualTo(-4);
        assertThat(ModuleRules.risk(List.of(it("Risco", null, -1)))).isEqualTo(ModuleRules.Risk.FALL);
        assertThat(ModuleRules.risk(List.of(it("Risco alto", null, -2)))).isEqualTo(ModuleRules.Risk.SELF_DAMAGE);
        assertThat(ModuleRules.requiredBase(List.of(it("Exige Base", "Exige Base: Kiba Dachi", -1)))).isEqualTo("Kiba Dachi");
        assertThat(ModuleRules.requiredBase(List.of(it("Exige Base", "Exige Base", -1)))).isEmpty();
        assertThat(ModuleRules.requiredBase(List.of())).isNull();
        assertThat(ModuleRules.chainSp(10)).isEqualTo(13);
        assertThat(ModuleRules.throwDice(Floor.SOFT, false)).isNull();
        assertThat(ModuleRules.throwDice(Floor.STANDARD, false)).isEqualTo("1d4");
        assertThat(ModuleRules.throwDice(Floor.SOFT, true)).isEqualTo("2d4");
        assertThat(ModuleRules.throwKp(Floor.STANDARD, false)).isZero();
        assertThat(ModuleRules.throwKp(Floor.SOFT, true)).isEqualTo(1);
    }
}
