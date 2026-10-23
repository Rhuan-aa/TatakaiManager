package com.tatakai.manager.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Traço do catálogo (Mongo). campaignId null = global (sistema); senão, extensão da campanha. */
@Document(collection = "traitDefinitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TraitDefinition {

    @Id
    private UUID id;

    private UUID campaignId;

    private String name;

    private TraitRarity rarity;

    private TraitCategory category;

    /** 1–20 (1–10 se DIVINE); null = traço de reposição "X". */
    private Integer rollNumber;

    private String description;

    @Builder.Default
    private List<TraitEffect> effects = new ArrayList<>();

    private BuildGains buildGains;

    private boolean developable;

    private boolean upgradable;

    private int version;

    private Instant updatedAt;

    /** Passivo ou ativo. */
    private TraitActivation activation;

    /** Requisitos (texto exibido; o Mestre decide). */
    private String requirements;

    /** Requisitos para desenvolver o traço (texto). */
    private String developmentRequirements;

    @Builder.Default
    private List<TraitVariation> variations = new ArrayList<>();
}
