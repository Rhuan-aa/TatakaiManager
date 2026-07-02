package com.tatakai.manager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Imagem (retrato) de um NPC, guardada em tabela separada para não pesar as
 * consultas de listagem/ficha. A PK é o próprio id do NPC (1:1).
 */
@Entity
@Table(name = "npc_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NpcImage {

    @Id
    @Column(name = "npc_id")
    private UUID npcId;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    // byte[] simples -> mapeado como bytea no Postgres (evita large object/oid do @Lob)
    @Column(name = "data", nullable = false, columnDefinition = "bytea")
    private byte[] data;
}
