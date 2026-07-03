package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Reserva de um slot (1..4) em um dia de TimeSkip — com um NPC, ou solo (treino,
 * estudo ou ação geral sem NPC). A constraint única (time_skip_day, npc, slot_number)
 * é a regra central para NPCs: dois jogadores não podem ocupar o mesmo slot do mesmo
 * NPC. Para atividades solo (npc_id nulo) o conflito é checado na aplicação: o mesmo
 * jogador não pode ter duas atividades solo no mesmo dia+slot (ver BookingService).
 */
@Entity
@Table(name = "bookings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_booking_slot",
                columnNames = {"time_skip_day_id", "npc_id", "slot_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "time_skip_day_id", nullable = false)
    private TimeSkipDay timeSkipDay;

    /** Id do NPC (ficha vive no Mongo). Nulo em atividades solo (sem NPC). */
    @Column(name = "npc_id")
    private UUID npcId;

    /** Nome do NPC — snapshot no momento do agendamento (evita ida ao Mongo para listar). Nulo em atividades solo. */
    @Column(name = "npc_name", length = 100)
    private String npcName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "slot_number", nullable = false)
    private short slotNumber;

    /** Nome da interação escolhida (snapshot do NPC no momento do agendamento). Nulo em atividades solo. */
    @Column(name = "interaction_name", length = 100)
    private String interactionName;

    /** Custo em pontos de ócio no momento do agendamento (registrado no log). Zero em atividades solo. */
    @Column(name = "idle_point_cost", nullable = false)
    private short idlePointCost;

    /** Tipo da atividade solo (treino/estudo/ação geral). Nulo quando o agendamento é com um NPC. */
    @Enumerated(EnumType.STRING)
    @Column(name = "solo_activity_type", length = 20)
    private SoloActivityType soloActivityType;

    /** Descrição livre da atividade solo. Nulo quando o agendamento é com um NPC. */
    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
