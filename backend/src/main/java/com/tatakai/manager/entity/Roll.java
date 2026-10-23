package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Rolagem de dados registrada (imutável, sem setters). */
@Entity
@Table(name = "rolls")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Roll {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "sheet_id")
    private UUID sheetId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(length = 50)
    private String expression;

    /** Array JSON dos resultados, ex.: "[3,5]". */
    @Column(name = "dice_results", columnDefinition = "text")
    private String diceResults;

    private int modifier;
    private int total;

    @Column(name = "natural_d20")
    private Integer naturalD20;

    @Column(length = 200)
    private String purpose;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
