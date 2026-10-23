package com.tatakai.manager.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/** Ganhos mecânicos de um traço/clã (embutido no Mongo e em snapshots jsonb). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuildGains {
    @Builder.Default
    private Map<Attribute, Integer> attributeFlat = new HashMap<>();
    @Builder.Default
    private Map<Attribute, Integer> attributePercent = new HashMap<>();
    private int maxHp;
    private int maxSp;
    private int maxKp;
    /** Chaves = nome de Attribute ou HP/SP/KP. */
    @Builder.Default
    private Map<String, Integer> limiters = new HashMap<>();
    private int freePoints;
}
