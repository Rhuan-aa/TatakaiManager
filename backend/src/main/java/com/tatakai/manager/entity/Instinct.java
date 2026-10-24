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

/** Instinto do catálogo (Mongo, RN-PROG-08). campaignId null = global; senão, extensão da campanha. */
@Document("instincts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Instinct {

    @Id
    private UUID id;

    private UUID campaignId;

    private String name;

    /** Patente mínima para escolher. */
    private KeikenRank rank;

    private String description;

    private int version;

    private Instant updatedAt;
}
