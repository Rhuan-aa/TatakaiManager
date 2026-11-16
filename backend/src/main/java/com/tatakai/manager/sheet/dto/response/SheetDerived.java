package com.tatakai.manager.sheet.dto.response;

import com.tatakai.manager.sheet.service.rules.SheetRules.LoadState;

import com.tatakai.manager.sheet.entity.Attribute;
import com.tatakai.manager.sheet.entity.AttributeBand;

import java.util.Map;

public record SheetDerived(
        Map<Attribute, Integer> mods,            // valor null p/ atributo 0
        Map<Attribute, AttributeBand> bands,
        Map<Attribute, Integer> limiters,        // sem TAL; null se RADAR oculto
        Integer hpLimiter, Integer spLimiter, Integer kpLimiter, // null se o recurso ou RADAR estiver oculto
        int weightMod, int carryKg, Boolean criticalState, // null se HP oculto
        double carriedKg, LoadState loadState   // Carga: peso do inventário e estado
) {}
