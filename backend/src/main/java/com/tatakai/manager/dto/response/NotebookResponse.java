package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.ArtBase;
import com.tatakai.manager.entity.ArtTechnique;
import com.tatakai.manager.entity.Graduation;
import com.tatakai.manager.entity.MoveKind;
import com.tatakai.manager.entity.NotebookStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Caderno Marcial e Forja de uma ficha (F4c, PLANO §19). `forge` null = nenhuma em andamento. */
public record NotebookResponse(List<Draft> drafts, ForgeView forge) {

    public record Draft(UUID id, MoveKind kind, String name, Graduation graduation, ArtTechnique technique, ArtBase base,
                        NotebookStatus status, String reviewNote, AssemblyCheck check, Instant createdAt) {}

    public record ForgeView(UUID id, UUID draftId, String name, int requiredPoints, int accumulatedPoints,
                            Instant startedAt) {}
}
