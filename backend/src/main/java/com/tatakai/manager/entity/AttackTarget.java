package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Um alvo de um ataque pendente, com a reação declarada e o desfecho. */
@Entity
@Table(name = "attack_targets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttackTarget {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "attack_id", nullable = false)
    private UUID attackId;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    /** null = aguardando resposta. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ReactionType reaction;

    @Column(name = "auto_resolved", nullable = false)
    private boolean autoResolved;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "reaction_roll_id")
    private UUID reactionRollId;

    /** Aliado que reagiu no lugar do alvo Inconsciente (Defender ou Contra-atacar); null = o próprio alvo. */
    @Column(name = "defender_id")
    private UUID defenderId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private AttackOutcome outcome;

    @Column(name = "mitigation_pct")
    private Integer mitigationPct;

    private Integer damage;

    @Column(name = "counter_damage")
    private Integer counterDamage;

    /** Contra-Ataque Tardio (F6d); null = não usado. */
    @Enumerated(EnumType.STRING)
    @Column(name = "late_counter", length = 10)
    private LateCounter lateCounter;

    /** SUCCESS: dano no atacante; FAIL: dano extra no alvo. */
    @Column(name = "late_counter_damage")
    private Integer lateCounterDamage;
}
