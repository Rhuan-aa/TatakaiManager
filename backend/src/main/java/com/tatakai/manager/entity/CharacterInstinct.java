package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Instinto escolhido pela ficha (snapshot do catálogo, RN-PROG-08). */
@Entity
@Table(name = "character_instincts", uniqueConstraints = @UniqueConstraint(columnNames = {"sheet_id", "instinct_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterInstinct {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CharacterSheet sheet;

    @Column(name = "instinct_id", nullable = false)
    private UUID instinctId;

    @Column(length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private KeikenRank rank;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
