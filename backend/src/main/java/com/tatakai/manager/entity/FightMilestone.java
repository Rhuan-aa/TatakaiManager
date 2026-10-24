package com.tatakai.manager.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** Marco de luta do catálogo (Mongo, RN-PROG-07). campaignId null = global; senão, extensão da campanha. */
@Document("fightMilestones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FightMilestone {

    /** Gatilho que a ficha consegue sugerir sozinha (sem motor de combate). */
    public static final String CRITICAL_STATE = "CRITICAL_STATE";

    @Id
    private UUID id;

    private UUID campaignId;

    private String name;

    private MilestoneCategory category;

    private String description;

    /** CRITICAL_STATE ou null (só manual). */
    private String autoTrigger;

    /** Assinatura: +1 ponto de graduação à arte usada na luta. */
    private boolean grantsArtPoint;

    /** Elo Fraco: só em luta Real ou Letal. */
    private boolean seriousOnly;

    /** Zebra: exige ao menos N outros Marcos de Desvantagem na mesma luta. */
    private Integer minOtherDisadvantage;

    private int version;

    private Instant updatedAt;
}
