package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.FightClassification;
import com.tatakai.manager.entity.KeikenRank;
import com.tatakai.manager.entity.OvercomeStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lutas, distribuições de PS e Instintos de uma ficha (F4b, PLANO §18). */
public record SheetProgressionResponse(List<Fight> fights, List<Overcome> distributions, List<Instinct> instincts,
                                       List<RivalView> rivals) {

    public record Fight(UUID id, UUID fightId, FightClassification classification, int elGained, int psGained,
                        List<String> milestones, Map<UUID, Integer> artEl, UUID timeSkipId, Short dayNumber,
                        LocalDate fightDate, String note, Instant createdAt) {}

    public record Overcome(UUID id, Map<String, Integer> allocations, OvercomeStatus status, String reviewNote,
                           Instant createdAt, Instant reviewedAt) {}

    public record RivalView(UUID id, String name, UUID npcId, String art, int fights, int readBonus,
                            List<String> knownTechniques) {}

    public record Instinct(UUID id, UUID instinctId, String name, KeikenRank rank, String description) {}
}
