package com.tatakai.manager.martial.dto.response;

import com.tatakai.manager.catalog.entity.ArtBase;
import com.tatakai.manager.catalog.entity.ArtTechnique;
import com.tatakai.manager.catalog.entity.Graduation;
import com.tatakai.manager.catalog.entity.MoveKind;

import java.util.UUID;

public record LooseMoveResponse(UUID id, MoveKind kind, String name, Graduation graduation, int complexity,
                                int trainingPoints, boolean visible, ArtTechnique technique, ArtBase base) {}
