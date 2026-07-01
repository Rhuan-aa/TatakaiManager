package com.tatakai.manager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tipo de interação oferecido por um NPC. Cada NPC define a própria lista.
 * O custo em pontos de treino é debitado do jogador que agenda a interação e
 * fica registrado no log do agendamento.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NpcInteraction {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** Custo em pontos de treino (>= 0). */
    @Column(name = "train_point_cost", nullable = false)
    private short trainPointCost;
}
