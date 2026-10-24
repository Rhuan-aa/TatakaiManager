package com.tatakai.manager.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Base de uma arte marcial (embutida no documento Mongo). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArtBase {
    private String name;
    private Graduation graduation;
    private String description;
    private List<ArtRule> rules = new ArrayList<>();
    private String effect;
    private String restrictions;
    /** Função da base. */
    private String function;
    private String assembly;
    private Integer complexity;
    private Integer trainingPoints;
    /** Montagem estruturada (F3a). */
    private List<AssemblyItem> items = new ArrayList<>();
}
