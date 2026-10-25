package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Contador de arte em combate (spec §8): Foco, Ritmo, Embalo... Relações por UUID solto. */
@Entity
@Table(name = "combat_counters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CombatCounter {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Column(name = "counter_key", nullable = false, length = 40)
    private String counterKey;

    /** Só contadores `perTarget`. */
    @Column(name = "target_participant_id")
    private UUID targetParticipantId;

    @Column(name = "counter_value", nullable = false)
    private int value;
}
