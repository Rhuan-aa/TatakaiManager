package com.tatakai.manager.sheet.service.rules;

import com.tatakai.manager.catalog.service.EquipmentCatalog.Container;
import com.tatakai.manager.sheet.entity.InventoryItem;
import com.tatakai.manager.sheet.entity.ItemLocation;
import com.tatakai.manager.sheet.exception.InvalidCharacterSheetException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Espaços do inventário. Mãos: 2 (arma de duas mãos ocupa as duas). Sem contêiner: 2 no Acesso Rápido e 4 em
 * Carregando; cada contêiner vestido (mochila, cinto, pochete…) soma os próprios espaços, com limite de peso.
 * Vestido e chão não têm limite de espaços. Uma pilha (quantidade > 1) ocupa um espaço.
 */
public final class InventoryRules {

    public static final int HANDS = 2;
    public static final Map<ItemLocation, Integer> BASE_SLOTS = Map.of(ItemLocation.QUICK, 2, ItemLocation.PACK, 4);

    private InventoryRules() { }

    /** Contêiner em que o item está de fato: só vale se o contêiner estiver vestido e guardar naquele local. */
    public static UUID areaOf(InventoryItem i, List<InventoryItem> inv, Function<InventoryItem, Container> containerOf) {
        if (i.getContainerId() == null || (i.getLocation() != ItemLocation.QUICK && i.getLocation() != ItemLocation.PACK)) {
            return null;
        }
        return inv.stream().filter(c -> c.getId() != null && c.getId().equals(i.getContainerId()))
                .filter(c -> c.getLocation() == ItemLocation.WORN)
                .filter(c -> { Container k = containerOf.apply(c); return k != null && k.location().equals(i.getLocation().name()); })
                .findFirst().map(InventoryItem::getId).orElse(null);
    }

    /** Confere se a área (local + contêiner) comporta o que está nela agora. */
    public static void check(List<InventoryItem> inv, ItemLocation loc, UUID containerId,
                             Function<InventoryItem, Container> containerOf, ToIntFunction<InventoryItem> hands) {
        switch (loc) {
            case HAND -> {
                int used = inv.stream().filter(i -> i.getLocation() == ItemLocation.HAND).mapToInt(hands).sum();
                if (used > HANDS) throw new InvalidCharacterSheetException("Mãos ocupadas: guarde algo antes");
            }
            case QUICK, PACK -> {
                List<InventoryItem> here = inv.stream().filter(i -> i.getLocation() == loc)
                        .filter(i -> Objects.equals(areaOf(i, inv, containerOf), containerId)).toList();
                String where = loc == ItemLocation.QUICK ? "Acesso Rápido" : "Carregando";
                if (containerId == null) {
                    int cap = BASE_SLOTS.get(loc);
                    if (here.size() > cap) {
                        throw new InvalidCharacterSheetException(where + " cheio (" + cap + " espaços): use um contêiner ou troque itens");
                    }
                    return;
                }
                InventoryItem box = inv.stream().filter(c -> containerId.equals(c.getId())).findFirst()
                        .orElseThrow(() -> new InvalidCharacterSheetException("Contêiner não encontrado"));
                Container k = containerOf.apply(box);
                if (box.getLocation() != ItemLocation.WORN || k == null || !k.location().equals(loc.name())) {
                    throw new InvalidCharacterSheetException(box.getName() + " precisa estar vestido e guardar em " + where);
                }
                if (here.size() > k.slots()) {
                    throw new InvalidCharacterSheetException(box.getName() + " cheio (" + k.slots() + " espaços)");
                }
                if (k.maxItemKg() != null) {
                    here.stream().filter(i -> i.getWeightKg() > k.maxItemKg()).findFirst().ifPresent(i -> {
                        throw new InvalidCharacterSheetException(box.getName() + " só leva itens de até " + kg(k.maxItemKg())
                                + " kg (" + i.getName() + " pesa " + kg(i.getWeightKg()) + " kg)");
                    });
                }
                double total = here.stream().mapToDouble(i -> i.getWeightKg() * Math.max(1, i.getQuantity())).sum();
                if (k.maxKg() != null && total > k.maxKg() + 1e-9) {
                    throw new InvalidCharacterSheetException(box.getName() + " aguenta até " + kg(k.maxKg()) + " kg");
                }
            }
            default -> { }
        }
    }

    /** Contêiner tirado do corpo não pode levar itens dentro. */
    public static void requireEmptyIfUnworn(List<InventoryItem> inv, InventoryItem box, Function<InventoryItem, Container> containerOf) {
        if (box.getLocation() == ItemLocation.WORN || containerOf.apply(box) == null) return;
        boolean hasItems = inv.stream().anyMatch(i -> i != box && box.getId() != null && box.getId().equals(i.getContainerId())
                && (i.getLocation() == ItemLocation.QUICK || i.getLocation() == ItemLocation.PACK));
        if (hasItems) throw new InvalidCharacterSheetException("Esvazie " + box.getName() + " antes de tirá-lo do corpo");
    }

    private static String kg(double v) {
        return (Math.round(v * 10) / 10.0 + "").replace(".0", "").replace('.', ',');
    }
}
