package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.ArtCategory;
import com.tatakai.manager.entity.CharacterArtStatus;
import com.tatakai.manager.entity.Graduation;

import java.util.UUID;

public record CharacterArtResponse(UUID id, UUID artId, String artName, ArtCategory category, Graduation graduation,
                                   CharacterArtStatus status) {}
