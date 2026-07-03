package com.tatakai.manager.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Atributos do NPC — todos opcionais (nulos quando não informados pelo Mestre).
 * Embutido no documento Mongo do NPC.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NpcAttributes {

    private Short forca;
    private Short destreza;
    private Short constituicao;
    private Short mental;
    private Short inteligencia;
    private Short talento;
}
