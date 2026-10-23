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

/** Clã de uma ficha — snapshot do catálogo. */
@Entity
@Table(name = "character_clans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterClan {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CharacterSheet sheet;

    @Column(name = "clan_id")
    private UUID clanId;

    @Column(name = "clan_version")
    private int clanVersion;

    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "build_gains", columnDefinition = "jsonb")
    private BuildGains buildGains;

    @Column(name = "acquired_at", nullable = false, updatable = false)
    private Instant acquiredAt;

    @PrePersist
    void onCreate() {
        if (acquiredAt == null) acquiredAt = Instant.now();
    }
}
