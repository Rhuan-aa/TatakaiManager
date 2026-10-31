package com.tatakai.manager.combat.service.rules;

import com.tatakai.manager.sheet.entity.InventoryItem;
import com.tatakai.manager.sheet.entity.ItemLocation;

import java.util.List;

/** Estoque de munição no inventário (plano §50.2). */
public final class AmmoRules {

    private AmmoRules() {}

    /** Σ quantity dos itens com consumableKey == ammoKey e location != DROPPED; ammoKey null → 0. */
    public static int available(List<InventoryItem> inv, String ammoKey) {
        if (inv == null || ammoKey == null) return 0;
        int total = 0;
        for (InventoryItem item : inv) {
            if (item != null && item.getLocation() != ItemLocation.DROPPED && ammoKey.equals(item.getConsumableKey())) {
                total += Math.max(0, item.getQuantity());
            }
        }
        return total;
    }

    /** Desconta até `wanted` (itens na ordem da lista, ignorando DROPPED), devolve quanto tirou; a quantidade vai a 0 mas o item não é removido. wanted <= 0 → 0. */
    public static int take(List<InventoryItem> inv, String ammoKey, int wanted) {
        if (inv == null || ammoKey == null || wanted <= 0) return 0;
        int remaining = wanted;
        for (InventoryItem item : inv) {
            if (remaining <= 0) break;
            if (item == null || item.getLocation() == ItemLocation.DROPPED) continue;
            if (!ammoKey.equals(item.getConsumableKey())) continue;
            int qty = item.getQuantity();
            if (qty <= 0) continue;
            int toTake = Math.min(qty, remaining);
            item.setQuantity(qty - toTake);
            remaining -= toTake;
        }
        return wanted - remaining;
    }
}
