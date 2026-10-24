package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Entrada do livro de treinos (spec §9): ganho pendente até o Repouso, ou aplicado na hora (técnica). */
@Entity
@Table(name = "training_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CharacterSheet sheet;

    @Column(name = "time_skip_id", nullable = false)
    private UUID timeSkipId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "day_number", nullable = false)
    private short dayNumber;

    @Column(length = 20, nullable = false)
    private String target;

    @Column(name = "technique_id")
    private UUID techniqueId;

    private int gain;

    /** Treino Pleno (false = Parcial). "full" é palavra reservada no Postgres. */
    @Column(name = "full_training")
    private boolean full;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TrainingEntryStatus status;

    @Column(length = 300)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
