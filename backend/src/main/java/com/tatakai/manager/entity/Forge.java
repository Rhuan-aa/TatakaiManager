package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Forja (spec §9.3): materializa um rascunho aprovado em 3 × C_final pontos de sessões. */
@Entity
@Table(name = "forges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Forge {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CharacterSheet sheet;

    @Column(name = "draft_id", nullable = false)
    private UUID draftId;

    @Column(length = 100)
    private String name;

    @Column(name = "required_points")
    private int requiredPoints;

    @Column(name = "accumulated_points")
    private int accumulatedPoints;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void onCreate() {
        if (startedAt == null) startedAt = Instant.now();
    }
}
