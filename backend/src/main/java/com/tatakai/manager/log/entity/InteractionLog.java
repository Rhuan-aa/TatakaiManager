package com.tatakai.manager.log.entity;

import com.tatakai.manager.auth.entity.User;
import com.tatakai.manager.campaign.entity.Campaign;
import com.tatakai.manager.timeskip.entity.Booking;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro narrativo de uma interação. Pode estar atrelado a um agendamento
 * (log do jogador) ou ser um log livre da campanha (adicionado pelo Mestre).
 */
@Entity
@Table(name = "interaction_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InteractionLog {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    /** Autor do log (jogador dono do agendamento ou o Mestre). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    /** Agendamento relacionado; nulo em logs livres do Mestre. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(nullable = false, columnDefinition = "text")
    private String narrative;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Preenchido quando o Mestre edita a narrativa; nulo se nunca editado. */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Pedido de jogador nasce PENDING; o restante nasce PUBLISHED. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(12) not null default 'PUBLISHED'")
    @Builder.Default
    private LogStatus status = LogStatus.PUBLISHED;

    /** Nota do Mestre ao aprovar/recusar o pedido. */
    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
