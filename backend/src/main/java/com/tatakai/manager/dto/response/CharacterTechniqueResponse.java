package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.Graduation;
import com.tatakai.manager.entity.Mastery;
import com.tatakai.manager.entity.MasteryBonus;
import com.tatakai.manager.entity.MoveKind;
import com.tatakai.manager.entity.TechniqueState;

import java.util.List;
import java.util.UUID;

/** `nextLevelCost`: pontos de maestria para o próximo nível (null se não sobe por pontos). */
public record CharacterTechniqueResponse(UUID id, UUID characterArtId, MoveKind kind, String name, Graduation graduation,
                                         int complexity, int trainingPoints, int trainingProgress, TechniqueState state,
                                         Mastery mastery, int masteryPoints, Integer nextLevelCost,
                                         List<MasteryBonus> bonuses) {}
