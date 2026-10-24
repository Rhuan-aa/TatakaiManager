package com.tatakai.manager.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Variação de uma técnica (1 a 2), cada uma com a sua montagem. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssemblyVariation {
    private String name;
    private TechniqueCore core;
    private List<AssemblyItem> items = new ArrayList<>();
}
