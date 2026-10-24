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

/** Arte marcial do catálogo (Mongo). campaignId null = global; senão, extensão da campanha. */
@Document("martialArts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MartialArt {

    @Id
    private UUID id;

    private UUID campaignId;

    private String name;

    private ArtCategory category;

    private String rank;

    private String martialType;

    @Builder.Default
    private List<String> attackSpecializations = new ArrayList<>();

    @Builder.Default
    private List<String> reactionSpecializations = new ArrayList<>();

    private String requirement;

    private String description;

    /** Critério de Vitória, Técnicas Emprestadas etc. (name = título). */
    @Builder.Default
    private List<ArtRule> sections = new ArrayList<>();

    @Builder.Default
    private List<ArtPassive> passives = new ArrayList<>();

    @Builder.Default
    private List<ArtBase> bases = new ArrayList<>();

    @Builder.Default
    private List<ArtTechnique> techniques = new ArrayList<>();

    private int version;

    private Instant updatedAt;
    /** null = APPROVED. */
    private ArtStatus status;
    /** Autor de uma Arte Criada por jogador. */
    private UUID authorId;
    private String reviewNote;
}
