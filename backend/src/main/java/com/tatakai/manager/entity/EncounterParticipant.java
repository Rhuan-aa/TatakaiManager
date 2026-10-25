package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "encounter_participants",
        uniqueConstraints = @UniqueConstraint(columnNames = {"encounter_id", "sheet_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncounterParticipant {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(name = "sheet_id", nullable = false)
    private UUID sheetId;

    /** Snapshot do nome da ficha. */
    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean npc;

    private Integer initiative;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "pa_current", nullable = false)
    private int paCurrent;

    @Column(name = "pa_bonus_next", nullable = false)
    private int paBonusNext;

    @Column(name = "pa_loss_next", nullable = false)
    private int paLossNext;

    @Column(name = "moves_this_turn", nullable = false)
    private int movesThisTurn;

    /** turn_counter do último turno iniciado; null antes do primeiro. */
    @Column(name = "turn_started_at")
    private Integer turnStartedAt;

    /** Participantes que o atacaram desde o início do turno dele (Luta contra Grupo, F6b). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attackers_this_round", columnDefinition = "jsonb")
    @Builder.Default
    private List<UUID> attackersThisRound = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) not null default 'REAL'")
    @Builder.Default
    private CombatClassification classification = CombatClassification.REAL;

    @Column(nullable = false, columnDefinition = "integer not null default 1")
    @Builder.Default
    private int team = 1;

    @Column(name = "adrenaline", nullable = false, columnDefinition = "integer not null default 0")
    private int adrenaline;

    @Column(name = "rounds_at_apex", nullable = false, columnDefinition = "integer not null default 0")
    private int roundsAtApex;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Hysteria hysteria;

    @Column(name = "save_next_used", nullable = false, columnDefinition = "boolean not null default false")
    private boolean saveNextUsed;

    @Column(name = "engaged_this_round", nullable = false, columnDefinition = "boolean not null default false")
    private boolean engagedThisRound;

    @Column(name = "damage_this_turn", nullable = false, columnDefinition = "integer not null default 0")
    private int damageThisTurn;

    @Column(name = "kp_zeroings", nullable = false, columnDefinition = "integer not null default 0")
    private int kpZeroings;

    @Column(name = "death_successes", nullable = false, columnDefinition = "integer not null default 0")
    private int deathSuccesses;

    @Column(name = "death_failures", nullable = false, columnDefinition = "integer not null default 0")
    private int deathFailures;

    /** Tentativas falhas de Estabilizar este lutador: o ND sobe +3 a cada uma. */
    @Column(name = "stabilize_failures", nullable = false, columnDefinition = "integer not null default 0")
    private int stabilizeFailures;

    /** CharacterArt escolhida como arte principal (F6d); null = padrão (maior graduação). */
    @Column(name = "main_art_id")
    private UUID mainArtId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, columnDefinition = "varchar(10) not null default 'NATURAL'")
    @Builder.Default
    private Guard guard = Guard.NATURAL;

    /** Última troca de guarda: o Timing só conta ataques resolvidos depois dela. */
    @Column(name = "guard_changed_at")
    private java.time.Instant guardChangedAt;

    @Column(name = "guard_changes_this_turn", nullable = false, columnDefinition = "integer not null default 0")
    private int guardChangesThisTurn;

    /** CharacterTechnique kind BASE ACTIVE em uso (RN-BASE-03); null = nenhuma. */
    @Column(name = "active_base_id")
    private UUID activeBaseId;

    /** Não nulo = Exausto-consciente: ND do próximo teste de Vontade. */
    @Column(name = "exhaustion_nd")
    private Integer exhaustionNd;
}
