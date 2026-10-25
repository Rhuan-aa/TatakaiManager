package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Linha do tempo do encontro (imutável). */
@Entity
@Table(name = "combat_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CombatEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(nullable = false)
    private int round;

    @Column(name = "turn_counter", nullable = false)
    private int turnCounter;

    @Column(name = "participant_id")
    private UUID participantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CombatEventType type;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
