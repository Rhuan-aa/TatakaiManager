package com.tatakai.manager.progression;

import com.tatakai.manager.progression.entity.RestQuality;
import com.tatakai.manager.progression.service.rules.TrainingRules;
import com.tatakai.manager.timeskip.entity.Booking;
import com.tatakai.manager.timeskip.entity.SoloActivityType;
import com.tatakai.manager.timeskip.entity.TimeSkipDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Projeção de Desgaste (§51.2)")
class TrainingRulesFatigueTest {

    private static Booking solo(int day, int slot, int cost, SoloActivityType t) {
        return Booking.builder().timeSkipDay(TimeSkipDay.builder().dayNumber((short) day).build())
                .slotNumber((short) slot).idlePointCost((short) cost).soloActivityType(t)
                .trainingTarget(t == SoloActivityType.TREINO ? "FOR" : null)
                .restQuality(t == SoloActivityType.REPOUSO ? RestQuality.SIMPLE : null).build();
    }

    @Test
    @DisplayName("treino sem repouso conta; repouso no dia zera; dia vazio não conta")
    void countsUnrestedDays() {
        var mine = List.of(solo(1, 1, 1, SoloActivityType.TREINO),
                solo(2, 1, 1, SoloActivityType.TREINO), solo(2, 2, 1, SoloActivityType.REPOUSO));
        assertEquals(1, TrainingRules.unrestedDays(mine, 3, 4));
    }

    @Test
    @DisplayName("dia cheio sem repouso conta, respeitando slotsPerDay")
    void fullDayCounts() {
        var mine = List.of(solo(1, 1, 2, SoloActivityType.ESTUDO));
        assertEquals(1, TrainingRules.unrestedDays(mine, 1, 2));
        assertEquals(0, TrainingRules.unrestedDays(mine, 1, 4));
    }
}
