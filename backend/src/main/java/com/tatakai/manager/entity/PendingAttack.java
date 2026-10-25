package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Ataque declarado (F6b): reserva PA/SP e aguarda as reações dos alvos. */
@Entity
@Table(name = "pending_attacks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingAttack {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(name = "attacker_id", nullable = false)
    private UUID attackerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private RollSourceType sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(length = 100)
    private String variation;

    @Enumerated(EnumType.STRING)
    @Column(name = "basic_attack", length = 20)
    private BasicAttack basicAttack;

    @Column(name = "source_name", nullable = false, length = 100)
    private String sourceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_kind", nullable = false, length = 20)
    private AttackActionKind actionKind;

    @Column(length = 40)
    private String region;

    @Column(name = "region_penalty", nullable = false)
    private int regionPenalty;

    @Column(name = "reserved_pa", nullable = false)
    private int reservedPa;

    @Column(name = "reserved_sp", nullable = false)
    private int reservedSp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private AttackStatus status;

    @Column(nullable = false)
    private int round;

    @Enumerated(EnumType.STRING)
    @Column(name = "strike_side", length = 10)
    private StrikeSide strikeSide;

    /** turn_counter do encontro quando resolveu: a janela do Contra-Ataque Tardio fecha no próximo next-turn. */
    @Column(name = "resolved_turn")
    private Integer resolvedTurn;

    @Column(name = "attack_roll_id")
    private UUID attackRollId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
