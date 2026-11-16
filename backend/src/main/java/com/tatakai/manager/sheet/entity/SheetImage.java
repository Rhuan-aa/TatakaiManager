package com.tatakai.manager.sheet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/** Imagem da ficha (retrato ou corpo inteiro), em tabela separada para não pesar as consultas da ficha. */
@Entity
@Table(name = "sheet_images", uniqueConstraints = @UniqueConstraint(columnNames = {"sheet_id", "kind"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SheetImage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "sheet_id", nullable = false)
    private UUID sheetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SheetImageKind kind;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "data", nullable = false, columnDefinition = "bytea")
    private byte[] data;
}
