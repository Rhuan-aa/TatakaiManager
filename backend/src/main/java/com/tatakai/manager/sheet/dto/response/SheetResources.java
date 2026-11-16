package com.tatakai.manager.sheet.dto.response;

/** Campos nulos = recurso (HP, SP ou PQ) oculto para quem consulta. */
public record SheetResources(Integer hpMax, Integer effectiveHpMax, Integer spMax, Integer kpMax,
        Integer currentHp, Integer currentSp, Integer currentKp, Integer openWounds) {}
