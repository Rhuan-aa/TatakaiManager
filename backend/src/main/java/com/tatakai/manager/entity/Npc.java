package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "npcs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Npc {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Embedded
    private NpcAttributes attributes;

    /** Dono do NPC (Mestre que o criou — acervo global). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User owner;

    /** Conhecimentos do NPC (antes "especializações"). Opcional. */
    @ElementCollection
    @CollectionTable(name = "npc_knowledge", joinColumns = @JoinColumn(name = "npc_id"))
    @Builder.Default
    private List<NpcDetail> knowledge = new ArrayList<>();

    /** Traços do NPC — mesmo formato {nome, descrição} dos conhecimentos. Opcional. */
    @ElementCollection
    @CollectionTable(name = "npc_traits", joinColumns = @JoinColumn(name = "npc_id"))
    @Builder.Default
    private List<NpcDetail> traits = new ArrayList<>();

    /** Specs (habilidades especiais) do NPC — mesmo formato {nome, descrição}. Opcional. */
    @ElementCollection
    @CollectionTable(name = "npc_specs", joinColumns = @JoinColumn(name = "npc_id"))
    @Builder.Default
    private List<NpcDetail> specs = new ArrayList<>();

    /** Tipos de interação oferecidos por este NPC (nome, descrição e custo). */
    @ElementCollection
    @CollectionTable(name = "npc_interactions", joinColumns = @JoinColumn(name = "npc_id"))
    @Builder.Default
    private List<NpcInteraction> interactions = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
