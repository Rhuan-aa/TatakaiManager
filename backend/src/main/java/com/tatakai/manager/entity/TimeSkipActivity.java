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
 * Atividade solo customizada, cadastrada pelo Mestre e disponível apenas neste
 * TimeSkip (ex.: "Reconstrução da vila" só existe enquanto durar aquele TimeSkip
 * específico) — ao contrário de Treino/Estudo/Ação geral, que são fixos e globais
 * (ver {@link SoloActivityType}).
 */
@Entity
@Table(name = "time_skip_activities",
        uniqueConstraints = @UniqueConstraint(columnNames = {"time_skip_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSkipActivity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "time_skip_id", nullable = false)
    private TimeSkip timeSkip;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    /** Custo em pontos de ócio (>= 0). */
    @Column(name = "idle_point_cost", nullable = false)
    private short idlePointCost;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
