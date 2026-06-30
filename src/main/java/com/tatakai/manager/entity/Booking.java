package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Reserva de um slot (1..4) de um NPC em um dia de TimeSkip.
 * A constraint única (time_skip_day, npc, slot_number) é a regra central:
 * dois jogadores não podem ocupar o mesmo slot do mesmo NPC.
 */
@Entity
@Table(name = "bookings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_booking_slot",
                columnNames = {"time_skip_day_id", "npc_id", "slot_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "time_skip_day_id", nullable = false)
    private TimeSkipDay timeSkipDay;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "npc_id", nullable = false)
    private Npc npc;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "slot_number", nullable = false)
    private short slotNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false, length = 50)
    private InteractionType interactionType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
