package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fechamento de luta de um participante (RN-PROG-07): EL, PS e Marcos confirmados pelo Mestre. */
@Entity
@Table(name = "fight_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FightRecord {

    @Id
    @GeneratedValue
    private UUID id;

    /** Agrupa os participantes da mesma luta. */
    @Column(name = "fight_id", nullable = false)
    private UUID fightId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CharacterSheet sheet;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private FightClassification classification;

    @Column(name = "el_gained")
    private int elGained;

    @Column(name = "ps_gained")
    private int psGained;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> milestones = new ArrayList<>();

    /** characterArtId → EL usado como pontos de graduação. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "art_el", columnDefinition = "jsonb")
    @Builder.Default
    private Map<UUID, Integer> artEl = new HashMap<>();

    @Column(name = "time_skip_id")
    private UUID timeSkipId;

    @Column(name = "day_number")
    private Short dayNumber;

    @Column(name = "fight_date", nullable = false)
    private LocalDate fightDate;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
