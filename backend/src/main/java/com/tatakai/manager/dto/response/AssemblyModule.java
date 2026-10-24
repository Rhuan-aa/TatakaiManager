package com.tatakai.manager.dto.response;

/** Módulo do catálogo de montagem (catalog/modules.json). `cost` é por unidade quando `perUnit`. */
public record AssemblyModule(String name, String group, String kind, int cost, boolean perUnit, Integer maxQty,
                             String appliesTo, String note) {}
