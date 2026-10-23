package com.tatakai.manager.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Variação de um traço (classe embutida no documento Mongo). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TraitVariation {
    private String name;
    private String description;
    private List<TraitEffect> effects;
    private String requirements;
}
