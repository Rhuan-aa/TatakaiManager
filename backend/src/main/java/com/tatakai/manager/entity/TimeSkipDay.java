package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Um dia dentro de um TimeSkip. Cada dia tem 4 slots de ócio (1..4),
 * representados implicitamente pelas reservas (Booking.slotNumber), mais um
 * slot extra ({@link #EXTRA_SLOT_NUMBER}) exclusivo para ações de custo zero —
 * no máximo uma por jogador por dia.
 */
@Entity
@Table(name = "time_skip_days",
        uniqueConstraints = @UniqueConstraint(columnNames = {"time_skip_id", "day_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSkipDay {

    public static final int SLOTS_PER_DAY = 4;

    /** Slot extra do dia: aceita somente ações de custo zero, uma por jogador por dia. */
    public static final short EXTRA_SLOT_NUMBER = SLOTS_PER_DAY + 1;

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "time_skip_id", nullable = false)
    private TimeSkip timeSkip;

    @Column(name = "day_number", nullable = false)
    private short dayNumber;
}
