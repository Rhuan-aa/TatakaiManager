package com.tatakai.manager.sheet;

import com.tatakai.manager.sheet.entity.DoseEntry;
import com.tatakai.manager.sheet.service.rules.DoseRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DoseRulesTest {

    @Test
    @DisplayName("§54 Dose repetida no dia dobra o Colateral; Analgésico devolve o Desgaste escondido no Repouso")
    void analgesicCollateral() {
        List<DoseEntry> d = DoseRules.take(List.of(), DoseRules.ANALGESICO, 2);
        assertThat(DoseRules.factor(d, DoseRules.ANALGESICO)).isEqualTo(2);
        assertThat(DoseRules.onRest(d)).isEqualTo(new DoseRules.RestEffect(2, 1, 0));
        d = DoseRules.take(d, DoseRules.ANALGESICO, 1);
        assertThat(d).hasSize(1);
        assertThat(DoseRules.onRest(d)).isEqualTo(new DoseRules.RestEffect(3, 3, 0));
    }

    @Test
    @DisplayName("§54 Calmante/Termogênico dão desvantagem na Iniciativa; Energético vale num combate só")
    void initiative() {
        assertThat(DoseRules.initiativeDisadvantage(List.of())).isFalse();
        assertThat(DoseRules.initiativeDisadvantage(DoseRules.take(null, DoseRules.TERMOGENICO, 0))).isTrue();
        List<DoseEntry> e = DoseRules.take(DoseRules.take(null, DoseRules.CALMANTE, 0), DoseRules.ENERGETICO, 0);
        assertThat(DoseRules.onRest(e)).isEqualTo(new DoseRules.RestEffect(0, 1, 1));
        assertThat(DoseRules.energized(e)).isTrue();
        assertThat(DoseRules.energized(DoseRules.spendEnergy(e))).isFalse();
    }
}
