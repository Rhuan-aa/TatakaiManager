package com.tatakai.manager.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Técnica de uma arte marcial (embutida no documento Mongo). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArtTechnique {
    private String name;
    private Graduation graduation;
    private String group;
    private String description;
    private List<ArtRule> rules = new ArrayList<>();
    private String effect;
    private String restrictions;
    private String range;
    private String side;
    private String assembly;
    private Integer complexity;
    private Integer trainingPoints;
    /** Texto, ex.: "21 + 10/turno". */
    private String spCost;
    /** Valor inteiro inicial do custo de SP. */
    private Integer spCostBase;
    /** Montagem estruturada (F3a). */
    private List<AssemblyVariation> variations = new ArrayList<>();
    /** Itens sobre a escolha entre variações; somam direto no final (ex.: Chute Fantasma). */
    private List<AssemblyItem> extras = new ArrayList<>();
}
