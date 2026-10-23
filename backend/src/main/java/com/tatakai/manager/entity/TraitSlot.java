package com.tatakai.manager.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Slot de traço na Construção Inicial (serializado em jsonb dentro de {@link BuildState}). */
@Data
@NoArgsConstructor
public class TraitSlot {
    private int index;
    private Integer rarityRoll;
    private TraitRarity rarity;
    private Integer tableRoll;
    private UUID traitId;
    private String traitName;
    private TraitSlotStatus status;
    private String note;
    private boolean divineBonus;
}
