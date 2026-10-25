package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.CombatClassification;
import com.tatakai.manager.entity.ConditionExpiry;
import com.tatakai.manager.entity.Foot;
import com.tatakai.manager.entity.Guard;
import com.tatakai.manager.entity.Hysteria;
import com.tatakai.manager.entity.MartialType;
import com.tatakai.manager.entity.EncounterStatus;

import java.util.List;
import java.util.UUID;

public record EncounterResponse(UUID id, UUID campaignId, String name, EncounterStatus status, int round,
                                int turnCounter, UUID currentParticipantId, List<ParticipantResponse> participants,
                                CombatClassification defaultClassification, java.time.Instant endedAt) {

    public record ParticipantResponse(UUID id, UUID sheetId, String name, boolean npc, Integer initiative,
                                      int orderIndex, int paCurrent, int paBonusNext, int paLossNext,
                                      int movesThisTurn, UUID ownerUserId, Resources resources,
                                      List<ConditionResponse> conditions, CombatClassification classification,
                                      int team, int adrenaline, String adrenalineTier, Hysteria hysteria,
                                      int roundsAtApex, int openWounds, int deathSuccesses, int deathFailures,
                                      boolean exhausted, UUID mainArtId, String mainArtName,
                                      MartialType martialType, Guard guard, Foot leadFoot, UUID activeBaseId,
                                      String activeBaseName, List<CounterResponse> counters, List<ArtRef> arts,
                                      List<ArtRef> bases) {
    }

    public record ArtRef(UUID id, String name) {
    }

    public record CounterResponse(UUID id, String key, String name, int value, int min, int max,
                                  UUID targetParticipantId) {
    }

    public record Resources(int currentHp, int hpMax, int currentSp, int spMax, int currentKp, int kpMax,
                        int physicalFatigue, int mentalFatigue) {
    }

    public record ConditionResponse(UUID id, String key, String name, ConditionExpiry expiresAt,
                                    Integer turnsRemaining, Integer intensity, UUID sourceParticipantId,
                                    String note) {
    }
}
