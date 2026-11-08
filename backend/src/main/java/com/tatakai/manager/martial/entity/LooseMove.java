package com.tatakai.manager.martial.entity;

import com.tatakai.manager.catalog.entity.ArtBase;
import com.tatakai.manager.catalog.entity.ArtTechnique;
import com.tatakai.manager.catalog.entity.Graduation;
import com.tatakai.manager.catalog.entity.MoveKind;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * Técnica ou base avulsa do catálogo da campanha (Mongo): sem arte, montada pelo Mestre. `visible`: os jogadores
 * só veem (e adquirem) as visíveis; o Mestre vê todas.
 */
@Document("looseMoves")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LooseMove {

    @Id
    private UUID id;

    private UUID campaignId;

    private MoveKind kind;

    private String name;

    private Graduation graduation;

    private int complexity;

    private int trainingPoints;

    private boolean visible;

    private ArtTechnique technique;

    private ArtBase base;

    private UUID createdBy;

    private Instant createdAt;
}
