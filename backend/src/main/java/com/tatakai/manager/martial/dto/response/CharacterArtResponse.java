package com.tatakai.manager.martial.dto.response;

import com.tatakai.manager.progression.service.rules.ProgressionRules.Requirement;

import com.tatakai.manager.catalog.entity.ArtCategory;
import com.tatakai.manager.catalog.entity.Graduation;
import com.tatakai.manager.martial.entity.CharacterArtStatus;

import java.util.List;
import java.util.UUID;

/** graduationCost/requirements: null quando não há próxima graduação. */
public record CharacterArtResponse(UUID id, UUID artId, String artName, ArtCategory category, Graduation graduation,
                                   CharacterArtStatus status, int graduationPoints, Integer graduationCost,
                                   List<Requirement> requirements) {}
