package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.ArtCategory;
import com.tatakai.manager.entity.CharacterArtStatus;
import com.tatakai.manager.entity.Graduation;
import com.tatakai.manager.service.rules.ProgressionRules.Requirement;

import java.util.List;
import java.util.UUID;

/** graduationCost/requirements: null quando não há próxima graduação. */
public record CharacterArtResponse(UUID id, UUID artId, String artName, ArtCategory category, Graduation graduation,
                                   CharacterArtStatus status, int graduationPoints, Integer graduationCost,
                                   List<Requirement> requirements) {}
