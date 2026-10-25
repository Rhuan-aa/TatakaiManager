package com.tatakai.manager.sheet.dto.response;

import com.tatakai.manager.sheet.service.rules.SheetRules.LoadState;

import com.tatakai.manager.sheet.entity.Attribute;
import com.tatakai.manager.sheet.entity.AttributeBand;

import java.util.Map;

public record SheetDerived(
        Map<Attribute, Integer> mods,            // valor null p/ atributo 0
        Map<Attribute, AttributeBand> bands,
        Map<Attribute, Integer> limiters,        // sem TAL
        int hpLimiter, int spLimiter, int kpLimiter,
        int weightMod, int carryKg, boolean criticalState,
        double carriedKg, LoadState loadState   // Carga: peso do inventário e estado
) {}
