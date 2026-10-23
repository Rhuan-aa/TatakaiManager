package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Visibilidade de uma seção da ficha de um NPC, por associação campanha-NPC, para os jogadores. */
@Entity
@Table(name = "npc_sheet_visibility",
        uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_npc_id", "section"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NpcSheetVisibility {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_npc_id", nullable = false)
    private CampaignNpc campaignNpc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SheetSection section;

    @Column(nullable = false)
    @Builder.Default
    private boolean visible = false;
}
