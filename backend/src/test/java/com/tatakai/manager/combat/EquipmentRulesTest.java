package com.tatakai.manager.combat;

import com.tatakai.manager.catalog.service.EquipmentCatalog;
import com.tatakai.manager.combat.service.rules.EquipmentRules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentRulesTest {

    @Test
    @DisplayName("§43: cobertura pela região, redução por tipo, a maior vale, categoria de peso")
    void rules() {
        assertThat(EquipmentRules.coverage(null)).isEqualTo("TRONCO");
        assertThat(EquipmentRules.coverage("Queixo")).isEqualTo("CABECA");
        assertThat(EquipmentRules.coverage("Têmpora")).isEqualTo("CABECA");
        assertThat(EquipmentRules.coverage("peito")).isEqualTo("TRONCO");
        assertThat(EquipmentRules.coverage("Joelho")).isEqualTo("PERNAS");
        assertThat(EquipmentRules.coverage("pé")).isEqualTo("PERNAS");
        assertThat(EquipmentRules.coverage("antebraço")).isEqualTo("BRACOS");
        EquipmentCatalog c = new EquipmentCatalog();
        EquipmentCatalog.Def antifaca = c.find("COLETE_ANTIFACA").orElseThrow();
        assertThat(EquipmentRules.reduction(antifaca.protection(), antifaca.cover(), "TRONCO", "SLASH", false)).isEqualTo(4);
        assertThat(EquipmentRules.reduction(antifaca.protection(), antifaca.cover(), "TRONCO", "PIERCE", true)).isZero();
        assertThat(EquipmentRules.reduction(antifaca.protection(), antifaca.cover(), "PERNAS", "SLASH", false)).isZero();
        EquipmentCatalog.Def balistico = c.find("COLETE_BALISTICO").orElseThrow();
        assertThat(EquipmentRules.reduction(balistico.protection(), balistico.cover(), "TRONCO", "PIERCE", true)).isEqualTo(6);
        assertThat(EquipmentRules.reduction(balistico.protection(), balistico.cover(), "TRONCO", "BLUNT", false)).isEqualTo(1);
        assertThat(EquipmentRules.best(List.of(1, 4, 2))).isEqualTo(4);
        assertThat(EquipmentRules.heaviest(List.of("LIGHT", "MEDIUM"))).isEqualTo("MEDIUM");
        assertThat(EquipmentRules.firstMove(4.5, "HEAVY")).isEqualTo(1.5);
        assertThat(EquipmentRules.firstMove(6, "MEDIUM")).isEqualTo(4);
        assertThat(EquipmentRules.majorSp("HEAVY")).isEqualTo(3);
    }
}
