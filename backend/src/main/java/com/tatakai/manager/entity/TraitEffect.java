package com.tatakai.manager.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Efeito textual de um traço (classe embutida no documento Mongo). */
@Data
@NoArgsConstructor
public class TraitEffect {
    private String name;
    private String text;

    public TraitEffect(String text) {
        this.text = text;
    }

    public TraitEffect(String name, String text) {
        this.name = name;
        this.text = text;
    }
}
