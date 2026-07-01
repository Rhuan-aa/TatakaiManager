package com.tatakai.manager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Interação oferecida por um NPC. Cada NPC define a própria lista, podendo haver
 * várias do mesmo tipo (ex.: dois "Treino" com títulos diferentes). O custo em
 * pontos de ócio é debitado do jogador que agenda e fica registrado no log.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NpcInteraction {

    /** Tipo/categoria da interação (ex.: Treino, Trabalho, Descanso, Outro). */
    @Column(name = "type", length = 50)
    private String type;

    /** Título da interação. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** Custo em pontos de ócio (>= 0). */
    @Column(name = "idle_point_cost", nullable = false)
    private short idlePointCost;
}
