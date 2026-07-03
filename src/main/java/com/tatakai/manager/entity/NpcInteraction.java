package com.tatakai.manager.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Interação oferecida por um NPC. Cada NPC define a própria lista, podendo haver
 * várias do mesmo tipo (ex.: dois "Treino" com títulos diferentes). O custo em
 * pontos de ócio é debitado do jogador que agenda e fica registrado no log.
 * Embutido no documento Mongo do NPC.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NpcInteraction {

    /** Tipo/categoria da interação (ex.: Treino, Trabalho, Descanso, Outro). */
    private String type;

    /** Título da interação. */
    private String name;

    private String description;

    /** Custo em pontos de ócio (>= 0). */
    private short idlePointCost;
}
