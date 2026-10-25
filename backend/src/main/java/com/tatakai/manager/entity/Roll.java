package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
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

    @Column(length = 200)
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

    /** Colunas da F5; nulos em rolagens antigas = PUBLIC/FREE. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RollChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RollMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 20)
    private RollSourceType sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "source_name", length = 100)
    private String sourceName;

    private Integer advantage;

    private Boolean critical;

    private Boolean fumble;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<RollPart> breakdown;

    @Column(name = "custom_channel_id")
    private UUID customChannelId;

    @Column(name = "damage_total")
    private Integer damageTotal;

    @Column(name = "edit_reason", length = 200)
    private String editReason;

    @Setter
    private Boolean revealed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
