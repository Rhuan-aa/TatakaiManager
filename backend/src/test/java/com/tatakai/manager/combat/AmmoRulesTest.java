package com.tatakai.manager.combat;

import com.tatakai.manager.combat.service.rules.AmmoRules;
import com.tatakai.manager.sheet.entity.InventoryItem;
import com.tatakai.manager.sheet.entity.ItemLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Estoque de munição (§50.2)")
class AmmoRulesTest {

    private static InventoryItem item(String key, int qty, ItemLocation loc) {
        InventoryItem i = new InventoryItem();
        i.setConsumableKey(key);
        i.setQuantity(qty);
        i.setLocation(loc);
        return i;
    }

    @Test
    @DisplayName("available ignora DROPPED e outras chaves")
    void availableIgnoresDroppedAndOtherKeys() {
        var inv = List.of(item("FLECHA", 5, ItemLocation.PACK), item("FLECHA", 9, ItemLocation.DROPPED),
                item("VIROTE", 7, ItemLocation.PACK), item("FLECHA", 2, ItemLocation.QUICK));
        assertEquals(7, AmmoRules.available(inv, "FLECHA"));
        assertEquals(0, AmmoRules.available(inv, null));
    }

    @Test
    @DisplayName("take parcial e entre vários itens")
    void takeAcrossItemsAndPartial() {
        var a = item("FLECHA", 3, ItemLocation.PACK);
        var b = item("FLECHA", 4, ItemLocation.QUICK);
        var inv = List.of(a, b);
        assertEquals(5, AmmoRules.take(inv, "FLECHA", 5));
        assertEquals(0, a.getQuantity());
        assertEquals(2, b.getQuantity());
        assertEquals(2, AmmoRules.take(inv, "FLECHA", 10));
        assertEquals(0, AmmoRules.take(inv, "FLECHA", 1));
    }
}
