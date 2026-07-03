package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Associação de um NPC (acervo global) a uma campanha.
 * O campo {@code visible} controla a visibilidade do NPC para os jogadores daquela campanha (US-20).
 */
@Entity
@Table(name = "campaign_npcs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "npc_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignNpc {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    /** Id do NPC (ficha vive no Mongo — {@link Npc} não é mais uma entidade JPA). */
    @Column(name = "npc_id", nullable = false)
    private UUID npcId;

    @Column(nullable = false)
    @Builder.Default
    private boolean visible = true;
}
