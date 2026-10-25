package com.tatakai.manager.martial.dto.response;

import com.tatakai.manager.catalog.entity.Graduation;
import com.tatakai.manager.catalog.entity.MoveKind;
import com.tatakai.manager.martial.entity.Mastery;
import com.tatakai.manager.martial.entity.MasteryBonus;
import com.tatakai.manager.martial.entity.TechniqueState;

import java.util.List;
import java.util.UUID;

/** `nextLevelCost`: pontos de maestria para o próximo nível (null se não sobe por pontos). */
public record CharacterTechniqueResponse(UUID id, UUID characterArtId, MoveKind kind, String name, Graduation graduation,
                                         int complexity, int trainingPoints, int trainingProgress, TechniqueState state,
                                         Mastery mastery, int masteryPoints, Integer nextLevelCost,
                                         List<MasteryBonus> bonuses, String borrowedFrom) {}
