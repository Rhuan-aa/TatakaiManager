package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.AttributeBand;

import java.util.Map;

public record SheetDerived(
        Map<Attribute, Integer> mods,            // valor null p/ atributo 0
        Map<Attribute, AttributeBand> bands,
        Map<Attribute, Integer> limiters,        // sem TAL
        int hpLimiter, int spLimiter, int kpLimiter,
        int weightMod, int carryKg, boolean criticalState
) {}
