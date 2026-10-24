package com.tatakai.manager.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Item da montagem de uma técnica/base. `cost` é o custo final do item (negativo em LIMITATION). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssemblyItem {
    /** Nome no catálogo de módulos; null = item único/livre. */
    private String module;
    private String label;
    private int qty = 1;
    private int cost;
    private AssemblyItemKind kind;
    private boolean nonAffine;
    private boolean onDie;
    private String note;
}
