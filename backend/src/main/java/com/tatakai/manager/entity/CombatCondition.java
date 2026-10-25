package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "combat_conditions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CombatCondition {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Column(name = "condition_key", nullable = false, length = 40)
    private String conditionKey;

    @Column(name = "source_participant_id")
    private UUID sourceParticipantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "expires_at", nullable = false, length = 30)
    private ConditionExpiry expiresAt;

    /** turn_counter no momento em que foi aplicada (RN-TURNO-01). */
    @Column(name = "applied_turn", nullable = false)
    private int appliedTurn;

    @Column(name = "turns_remaining")
    private Integer turnsRemaining;

    private Integer intensity;

    /** Contador da condição: testes de Congelando feitos, sucessos contra o Envenenado (§33). */
    private Integer progress;

    @Column(length = 200)
    private String note;
}
