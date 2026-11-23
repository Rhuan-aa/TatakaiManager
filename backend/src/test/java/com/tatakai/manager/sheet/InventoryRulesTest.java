package com.tatakai.manager.sheet;

import com.tatakai.manager.catalog.service.EquipmentCatalog.Container;
import com.tatakai.manager.sheet.entity.InventoryItem;
import com.tatakai.manager.sheet.entity.ItemLocation;
import com.tatakai.manager.sheet.exception.InvalidCharacterSheetException;
import com.tatakai.manager.sheet.service.rules.InventoryRules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Espaços do inventário e contêineres")
class InventoryRulesTest {

    private static final Container BELT = new Container("QUICK", 2, 0.5, null);
    private static final Container BAG = new Container("PACK", 3, null, 5.0);
    private final Function<InventoryItem, Container> containerOf =
            i -> "BELT".equals(i.getEquipmentKey()) ? BELT : "BAG".equals(i.getEquipmentKey()) ? BAG : null;

    private InventoryItem item(String name, ItemLocation loc, double kg) {
        return InventoryItem.builder().id(UUID.randomUUID()).name(name).quantity(1).weightKg(kg).location(loc).build();
    }

    private void check(List<InventoryItem> inv, ItemLocation loc, UUID box) {
        InventoryRules.check(inv, loc, box, containerOf, i -> 1);
    }

    @Test
    @DisplayName("Acesso Rápido sem contêiner tem 2 espaços")
    void baseQuick() {
        List<InventoryItem> inv = new ArrayList<>(List.of(item("a", ItemLocation.QUICK, 1), item("b", ItemLocation.QUICK, 1)));
        check(inv, ItemLocation.QUICK, null);
        inv.add(item("c", ItemLocation.QUICK, 1));
        assertThrows(InvalidCharacterSheetException.class, () -> check(inv, ItemLocation.QUICK, null));
    }

    @Test
    @DisplayName("cinto vestido soma espaços só para itens leves")
    void belt() {
        InventoryItem belt = item("Cinto", ItemLocation.WORN, 0.5);
        belt.setEquipmentKey("BELT");
        InventoryItem light = item("Gaze", ItemLocation.QUICK, 0.2);
        light.setContainerId(belt.getId());
        List<InventoryItem> inv = new ArrayList<>(List.of(belt, light, item("a", ItemLocation.QUICK, 1), item("b", ItemLocation.QUICK, 1)));
        check(inv, ItemLocation.QUICK, belt.getId());
        check(inv, ItemLocation.QUICK, null);
        light.setWeightKg(2);
        InvalidCharacterSheetException e = assertThrows(InvalidCharacterSheetException.class, () -> check(inv, ItemLocation.QUICK, belt.getId()));
        assertTrue(e.getMessage().contains("0,5 kg"));
    }

    @Test
    @DisplayName("mochila limita o peso total; contêiner tirado do corpo precisa estar vazio")
    void bag() {
        InventoryItem bag = item("Mochila", ItemLocation.WORN, 1);
        bag.setEquipmentKey("BAG");
        InventoryItem heavy = item("Pedras", ItemLocation.PACK, 2);
        heavy.setQuantity(3);
        heavy.setContainerId(bag.getId());
        List<InventoryItem> inv = List.of(bag, heavy);
        assertThrows(InvalidCharacterSheetException.class, () -> check(inv, ItemLocation.PACK, bag.getId()));
        bag.setLocation(ItemLocation.PACK);
        assertThrows(InvalidCharacterSheetException.class, () -> InventoryRules.requireEmptyIfUnworn(inv, bag, containerOf));
        assertNull(InventoryRules.areaOf(heavy, inv, containerOf), "mochila tirada: item volta ao espaço básico");
    }

    @Test
    @DisplayName("duas mãos no máximo")
    void hands() {
        List<InventoryItem> inv = List.of(item("a", ItemLocation.HAND, 1), item("b", ItemLocation.HAND, 1));
        InventoryRules.check(inv, ItemLocation.HAND, null, containerOf, i -> 1);
        assertThrows(InvalidCharacterSheetException.class,
                () -> InventoryRules.check(inv, ItemLocation.HAND, null, containerOf, i -> "a".equals(i.getName()) ? 2 : 1));
    }
}
