package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Visibilidade de uma seção da ficha de um membro (jogador) para os DEMAIS jogadores.
 * A seção fica visível a terceiros quando não foi ocultada nem pelo Mestre nem pelo próprio.
 * O Mestre sempre enxerga tudo; o dono sempre enxerga a própria ficha (US-21, US-22).
 */
@Entity
@Table(name = "character_sheet_visibility",
        uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_member_id", "section"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterSheetVisibility {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_member_id", nullable = false)
    private CampaignMember campaignMember;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SheetSection section;

    @Column(name = "hidden_by_master", nullable = false)
    @Builder.Default
    private boolean hiddenByMaster = false;

    @Column(name = "hidden_by_self", nullable = false)
    @Builder.Default
    private boolean hiddenBySelf = false;
}
