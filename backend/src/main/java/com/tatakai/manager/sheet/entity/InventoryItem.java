package com.tatakai.manager.sheet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Item do inventário de uma ficha digital. */
@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CharacterSheet sheet;

    @Column(nullable = false, length = 100)
    private String name;

    private int quantity;

    @Column(columnDefinition = "text")
    private String description;

    /** Peso unitário em kg (Carga, capítulo de Condições). */
    @Column(name = "weight_kg", nullable = false, columnDefinition = "double precision default 0")
    private double weightKg;

    /** Arma do catálogo (`catalog/weapons.json`), proteção/equipamento ou consumível; null = item comum (§42–§44). */
    @Column(name = "weapon_key", length = 40)
    private String weaponKey;

    @Column(name = "equipment_key", length = 40)
    private String equipmentKey;

    @Column(name = "consumable_key", length = 40)
    private String consumableKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, columnDefinition = "varchar(10) not null default 'PACK'")
    @Builder.Default
    private ItemLocation location = ItemLocation.PACK;

    /** Disparos restantes antes de recarregar (Capacidade). */
    @Column(nullable = false, columnDefinition = "integer not null default 0")
    private int loaded;

    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean jammed;

    /** Engatilhamento Lento: armada para o próximo disparo. */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean cocked;

    /** Quebrada (inutilizável) ou danificada (−1 dado até o reparo). */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean broken;

    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean damaged;

    /** Empunhadura Variável usada com as duas mãos. */
    @Column(name = "two_handed", nullable = false, columnDefinition = "boolean not null default false")
    private boolean twoHanded;
}
