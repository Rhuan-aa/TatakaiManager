package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Técnica ou base de uma ficha (spec §7.2). Nome, graduação, complexidade e PT são snapshot da arte. */
@Entity
@Table(name = "character_techniques")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterTechnique {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CharacterSheet sheet;

    /** null = técnica/base avulsa (vinda da Forja, F4c). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_art_id")
    private CharacterArt characterArt;

    public UUID artIdOrNull() {
        return characterArt == null ? null : characterArt.getId();
    }

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MoveKind kind;

    @Column(length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Graduation graduation;

    private int complexity;

    @Column(name = "training_points")
    private int trainingPoints;

    @Column(name = "training_progress")
    private int trainingProgress;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TechniqueState state;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Mastery mastery;

    @Column(name = "mastery_points")
    private int masteryPoints;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<MasteryBonus> bonuses = new ArrayList<>();

    /** Arte de origem de uma técnica emprestada (null = da própria arte). */
    @Column(name = "borrowed_from", length = 100)
    private String borrowedFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
