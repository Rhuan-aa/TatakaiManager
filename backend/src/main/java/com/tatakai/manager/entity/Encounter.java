package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Encontro de combate de uma campanha (no máximo um não encerrado por vez). */
@Entity
@Table(name = "encounters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Encounter {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EncounterStatus status;

    @Column(nullable = false)
    private int round;

    /** Índice global de turno: cresce a cada turno iniciado. */
    @Column(name = "turn_counter", nullable = false)
    private int turnCounter;

    /** Posição na ordem de iniciativa do participante atual. */
    @Column(name = "current_index", nullable = false)
    private int currentIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_classification", nullable = false, length = 20,
            columnDefinition = "varchar(20) not null default 'REAL'")
    @Builder.Default
    private CombatClassification defaultClassification = CombatClassification.REAL;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
