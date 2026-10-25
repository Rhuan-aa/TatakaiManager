package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/** Ficha digital: uma por membro PLAYER ({@link CampaignMember}) OU uma por NPC do acervo ({@code npcId}). */
@Entity
@Table(name = "character_sheets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterSheet {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "campaign_member_id", nullable = true, unique = true)
    private CampaignMember campaignMember;

    @Column(name = "npc_id", unique = true)
    private UUID npcId;

    @Column(name = "npc_owner_id")
    private UUID npcOwnerId;

    @Column(length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "build_status", length = 20)
    private BuildStatus buildStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_type", length = 20)
    private BodyType bodyType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Dominance dominance;

    @Column(name = "height_m")
    private double heightM;

    @Column(name = "weight_kg")
    private double weightKg;

    private int str;
    private int dex;
    private int con;

    /** Inteligência; coluna "intl" porque "int" é palavra reservada. */
    @Column(name = "intl")
    private int intl;

    private int cha;
    private int men;
    private int tal;

    private int hpMax;
    private int spMax;
    private int kpMax;
    private int currentHp;
    private int currentSp;
    private int currentKp;

    private int openWounds;

    /** Sequela de Nocaute pendente (regra opcional): o próximo combate começa com 1 PQ a menos. */
    @Column(name = "knockout_sequel", nullable = false, columnDefinition = "boolean not null default false")
    private boolean knockoutSequel;
    private int physicalFatigue;
    private int mentalFatigue;
    private int fightExperience;
    private int pendingOvercomePoints;

    /** EL acumulado vindo de sparring (teto por patente, não reseta). */
    @Column(name = "sparring_experience", nullable = false, columnDefinition = "integer not null default 0")
    private int sparringExperience;

    /** Dos PS pendentes, quantos vieram de luta Real/Letal e podem cruzar Barreira. */
    @Column(name = "barrier_overcome_points", nullable = false, columnDefinition = "integer not null default 0")
    private int barrierOvercomePoints;
    private int money;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "sheet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt")
    @Builder.Default
    private List<PermanentInjury> injuries = new ArrayList<>();

    @OneToMany(mappedBy = "sheet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name")
    @Builder.Default
    private List<InventoryItem> inventory = new ArrayList<>();

    /** Ajustes de limitador (chaves: nome de Attribute, HP, SP, KP). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "limiter_adjustments", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Integer> limiterAdjustments = new HashMap<>();

    /** Meio-ponto de treino guardado além do Limitador, por alvo (RN-PROG-04). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "limiter_carry", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Integer> limiterCarry = new HashMap<>();

    @OneToMany(mappedBy = "sheet", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CharacterTrait> traits = new ArrayList<>();

    @OneToMany(mappedBy = "sheet", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CharacterClan> clans = new ArrayList<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isNpcSheet() {
        return npcId != null;
    }

    public int getAttribute(Attribute a) {
        return switch (a) {
            case STR -> str;
            case DEX -> dex;
            case CON -> con;
            case INT -> intl;
            case CHA -> cha;
            case MEN -> men;
            case TAL -> tal;
        };
    }

    public void setAttribute(Attribute a, int v) {
        switch (a) {
            case STR -> str = v;
            case DEX -> dex = v;
            case CON -> con = v;
            case INT -> intl = v;
            case CHA -> cha = v;
            case MEN -> men = v;
            case TAL -> tal = v;
        }
    }
}
