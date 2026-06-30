package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    @ElementCollection
    @CollectionTable(name = "npc_specs", joinColumns = @JoinColumn(name = "npc_id"))
    @Builder.Default
    private List<NpcSpec> specs = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "npc_traits", joinColumns = @JoinColumn(name = "npc_id"))
    @Column(name = "name", nullable = false, length = 100)
    @Builder.Default
    private List<String> traits = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "npc_interaction_types", joinColumns = @JoinColumn(name = "npc_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    @Builder.Default
    private Set<InteractionType> interactionTypes = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
