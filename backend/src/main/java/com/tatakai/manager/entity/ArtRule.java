package com.tatakai.manager.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Seção/regra nomeada de uma arte, base ou técnica (ex.: Critério de Vitória). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArtRule {
    /** Título. */
    private String name;
    private String text;
}
