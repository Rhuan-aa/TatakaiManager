package com.tatakai.manager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Atributos do NPC — todos opcionais (nulos quando não informados pelo Mestre).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NpcAttributes {

    @Column(name = "attr_for")
    private Short forca;

    @Column(name = "attr_des")
    private Short destreza;

    @Column(name = "attr_con")
    private Short constituicao;

    @Column(name = "attr_men")
    private Short mental;

    @Column(name = "attr_int")
    private Short inteligencia;

    @Column(name = "attr_tal")
    private Short talento;
}
