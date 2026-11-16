package com.tatakai.manager.catalog.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/** Imagem opcional de item do catálogo estático (global ou da campanha), com miniatura. */
@Entity
@Table(name = "catalog_item_images",
        uniqueConstraints = @UniqueConstraint(columnNames = {"kind", "item_key", "campaign_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogItemImage {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CatalogItemKind kind;

    @Column(name = "item_key", nullable = false, length = 40)
    private String itemKey;

    /** Nulo = imagem global. */
    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "data", nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Column(name = "thumb_content_type", nullable = false, length = 100)
    private String thumbContentType;

    @Column(name = "thumb", nullable = false, columnDefinition = "bytea")
    private byte[] thumb;
}
