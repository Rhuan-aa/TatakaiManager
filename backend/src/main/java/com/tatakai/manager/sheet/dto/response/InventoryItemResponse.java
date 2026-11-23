package com.tatakai.manager.sheet.dto.response;

import com.tatakai.manager.sheet.entity.InventoryItem;
import com.tatakai.manager.sheet.entity.ItemLocation;

import java.util.UUID;

public record InventoryItemResponse(UUID id, String name, int quantity, String description, double weightKg,
                                    String weaponKey, String equipmentKey, String consumableKey,
                                    com.tatakai.manager.sheet.entity.ItemLocation location, int loaded, boolean jammed,
                                    boolean cocked, boolean broken, boolean damaged, boolean twoHanded, UUID containerId) {

    public static InventoryItemResponse of(com.tatakai.manager.sheet.entity.InventoryItem i) {
        return new InventoryItemResponse(i.getId(), i.getName(), i.getQuantity(), i.getDescription(), i.getWeightKg(),
                i.getWeaponKey(), i.getEquipmentKey(), i.getConsumableKey(), i.getLocation(), i.getLoaded(),
                i.isJammed(), i.isCocked(), i.isBroken(), i.isDamaged(), i.isTwoHanded(), i.getContainerId());
    }
}
