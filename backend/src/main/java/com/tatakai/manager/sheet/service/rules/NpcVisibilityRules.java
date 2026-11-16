package com.tatakai.manager.sheet.service.rules;

import com.tatakai.manager.sheet.entity.NpcSheetVisibility;
import com.tatakai.manager.sheet.entity.SheetSection;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Regras puras: quais seções da ficha de NPC um jogador enxerga. */
public final class NpcVisibilityRules {

    private NpcVisibilityRules() {}

    /** Seções liberadas. O antigo RESOURCES, se liberado, vale por HP, SP e KP. */
    public static Set<SheetSection> visible(List<NpcSheetVisibility> rows) {
        Set<SheetSection> out = EnumSet.noneOf(SheetSection.class);
        for (NpcSheetVisibility v : rows) {
            if (v.isVisible()) out.add(v.getSection());
        }
        if (out.contains(SheetSection.RESOURCES)) {
            out.add(SheetSection.HP);
            out.add(SheetSection.SP);
            out.add(SheetSection.KP);
        }
        out.retainAll(EnumSet.copyOf(SheetSection.NPC_SECTIONS));
        return out;
    }

    /** Complemento de {@link #visible}, usado como "ocultas" em toResponse (inclui RESOURCES se nada de recursos está liberado). */
    public static Set<SheetSection> hidden(Set<SheetSection> visible) {
        Set<SheetSection> hidden = EnumSet.copyOf(SheetSection.NPC_SECTIONS);
        hidden.removeAll(visible);
        return hidden;
    }
}
