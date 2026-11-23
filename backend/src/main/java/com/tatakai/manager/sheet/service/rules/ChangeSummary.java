package com.tatakai.manager.sheet.service.rules;

import com.tatakai.manager.sheet.dto.request.GmAdjustmentRequest;
import com.tatakai.manager.sheet.entity.CharacterSheet;
import com.tatakai.manager.sheet.entity.InventoryItem;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Texto curto (pt-BR) de cada ajuste de um pedido, com o valor atual da ficha quando faz sentido. */
public final class ChangeSummary {

    private static final Map<String, String> ATTR = Map.of("STR", "FOR", "DEX", "DES", "CON", "CON", "INT", "INT",
            "CHA", "CAR", "MEN", "MEN", "TAL", "TAL");
    private static final Map<String, String> RES = Map.of("HP", "HP", "SP", "SP", "KP", "PQ");

    private ChangeSummary() { }

    public static List<String> describeAll(CharacterSheet s, List<GmAdjustmentRequest> changes) {
        return changes.stream().map(c -> describe(s, c)).toList();
    }

    public static String describe(CharacterSheet s, GmAdjustmentRequest c) {
        Integer v = c.value();
        return switch (c.action()) {
            case SET_ATTRIBUTE -> {
                String f = c.field() == null ? "?" : c.field();
                Integer before = attr(s, f);
                yield ATTR.getOrDefault(f, f) + ": " + (before == null ? "" : before + " → ") + v;
            }
            case SET_MAX -> {
                String f = c.field() == null ? "?" : c.field();
                Integer before = switch (f) { case "HP" -> s.getHpMax(); case "SP" -> s.getSpMax(); case "KP" -> s.getKpMax(); default -> null; };
                yield RES.getOrDefault(f, f) + " máx.: " + (before == null ? "" : before + " → ") + v;
            }
            case SET_RESOURCE -> RES.getOrDefault(String.valueOf(c.field()), String.valueOf(c.field())) + " atual: " + v;
            case GRANT_FIGHT_EXPERIENCE -> "EL: " + s.getFightExperience() + " → " + (s.getFightExperience() + nz(v));
            case ADJUST_OVERCOME_POINTS -> "PS pendentes: " + s.getPendingOvercomePoints() + " → " + (s.getPendingOvercomePoints() + nz(v));
            case ADJUST_MONEY -> "Dinheiro: " + s.getMoney() + " → " + (s.getMoney() + nz(v));
            case ADJUST_FATIGUE -> "Desgaste " + ("MENTAL".equals(c.field()) ? "mental" : "físico") + " " + signed(v);
            case SET_OPEN_WOUNDS -> "Feridas abertas: " + s.getOpenWounds() + " → " + v;
            case ADD_TRAIT -> "Adicionar traço: " + (c.field() == null ? "?" : c.field());
            case REMOVE_TRAIT -> "Remover traço: " + s.getTraits().stream().filter(t -> t.getId().equals(c.targetId()))
                    .map(t -> t.getName()).findFirst().orElse("?");
            case ADD_ITEM -> "Adicionar item: " + (c.item() == null ? "?" : c.item().name() + " ×" + c.item().quantity());
            case UPDATE_ITEM -> "Alterar item: " + itemName(s, c.targetId()) + (c.item() == null ? "" : " → " + c.item().name() + " ×" + c.item().quantity());
            case REMOVE_ITEM -> "Remover item: " + itemName(s, c.targetId());
            case ADD_INJURY -> "Adicionar ferimento" + (c.injury() == null ? "" : ": " + c.injury().region());
            case TREAT_INJURY -> "Tratar ferimento";
            case REMOVE_INJURY -> "Remover ferimento";
            case SET_MISSING_PART -> (Boolean.TRUE.equals(c.missing()) ? "Marcar parte perdida: " : "Restaurar parte: ") + c.bodyPart();
        };
    }

    private static Integer attr(CharacterSheet s, String f) {
        return switch (f) {
            case "STR" -> s.getStr(); case "DEX" -> s.getDex(); case "CON" -> s.getCon(); case "INT" -> s.getIntl();
            case "CHA" -> s.getCha(); case "MEN" -> s.getMen(); case "TAL" -> s.getTal(); default -> null;
        };
    }

    private static String itemName(CharacterSheet s, UUID id) {
        return s.getInventory().stream().filter(i -> i.getId().equals(id)).map(InventoryItem::getName).findFirst().orElse("?");
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String signed(Integer v) {
        return nz(v) >= 0 ? "+" + nz(v) : String.valueOf(v);
    }
}
