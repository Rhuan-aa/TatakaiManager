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

/**
 * Ficha textual do NPC — vive no MongoDB (nome, descrição, atributos, conhecimentos,
 * traços, specs, interações). A imagem (retrato) fica à parte, em Postgres/{@link NpcImage}
 * (ver DEPLOY.md — texto no Mongo, imagem no Postgres para caber nas cotas free de cada um).
 */
@Document(collection = "npcs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Npc {

    @Id
    private UUID id;

    private String name;

    private String description;

    private NpcAttributes attributes;

    /** Id do dono do NPC (Mestre que o criou — acervo global). User vive no Postgres. */
    private UUID ownerId;

    /** Conhecimentos do NPC (antes "especializações"). Opcional. */
    @Builder.Default
    private List<NpcDetail> knowledge = new ArrayList<>();

    /** Traços do NPC — mesmo formato {nome, descrição} dos conhecimentos. Opcional. */
    @Builder.Default
    private List<NpcDetail> traits = new ArrayList<>();

    /** Specs (habilidades especiais) do NPC — mesmo formato {nome, descrição}. Opcional. */
    @Builder.Default
    private List<NpcDetail> specs = new ArrayList<>();

    /** Tipos de interação oferecidos por este NPC (nome, descrição e custo). */
    @Builder.Default
    private List<NpcInteraction> interactions = new ArrayList<>();

    private Instant createdAt;
}
