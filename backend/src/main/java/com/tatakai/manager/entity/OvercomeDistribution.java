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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pedido do jogador para distribuir PS pendentes; o Mestre aprova (aplica) ou recusa. */
@Entity
@Table(name = "overcome_distributions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvercomeDistribution {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CharacterSheet sheet;

    /** alvo (STR..MEN, HP, SP) → PS. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Integer> allocations = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private OvercomeStatus status;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
