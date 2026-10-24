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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Registro de Rivais (spec §9.3): +1 de Leitura por luta (máx. 3). */
@Entity
@Table(name = "rivals", uniqueConstraints = @UniqueConstraint(columnNames = {"sheet_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rival {

    public static final int MAX_READ = 3;

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CharacterSheet sheet;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(name = "npc_id")
    private UUID npcId;

    @Column(length = 100)
    private String art;

    private int fights;

    @Column(name = "read_bonus")
    private int readBonus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "known_techniques", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> knownTechniques = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
