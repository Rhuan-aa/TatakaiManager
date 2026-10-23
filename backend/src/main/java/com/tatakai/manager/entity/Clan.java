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

/** Clã do catálogo (Mongo). campaignId null = global; senão, extensão da campanha. */
@Document(collection = "clans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Clan {

    @Id
    private UUID id;

    private UUID campaignId;

    private String name;

    /** 1–20; null = só por escolha. */
    private Integer rollNumber;

    private String description;

    private BuildGains buildGains;

    /** Itens/conhecimentos/specs hereditárias (texto). */
    @Builder.Default
    private List<String> heritage = new ArrayList<>();

    private int version;

    private Instant updatedAt;
}
