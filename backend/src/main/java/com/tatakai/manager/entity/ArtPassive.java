package com.tatakai.manager.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Passiva de uma arte marcial (embutida no documento Mongo). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArtPassive {
    private String name;
    /** Título do bloco de origem, ex.: "Kumite (組手)". */
    private String group;
    private String description;
}
