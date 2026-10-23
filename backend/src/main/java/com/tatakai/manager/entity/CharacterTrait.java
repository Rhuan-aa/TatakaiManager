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
import java.util.UUID;

/** Traço adquirido por uma ficha — snapshot do catálogo (RN-TRAIT-02). */
@Entity
@Table(name = "character_traits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterTrait {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CharacterSheet sheet;

    @Column(name = "trait_id")
    private UUID traitId;

    @Column(name = "trait_version")
    private int traitVersion;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TraitRarity rarity;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TraitCategory category;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "build_gains", columnDefinition = "jsonb")
    private BuildGains buildGains;

    @Enumerated(EnumType.STRING)
    @Column(name = "acquired_in", length = 20)
    private TraitAcquisition acquiredIn;

    @Column(name = "gains_applied")
    private boolean gainsApplied;

    @Column(name = "acquired_at", nullable = false, updatable = false)
    private Instant acquiredAt;

    @PrePersist
    void onCreate() {
        if (acquiredAt == null) acquiredAt = Instant.now();
    }
}
