package com.tatakai.manager.timeskip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Envio dos agendamentos de um jogador, num TimeSkip, para aprovação do Mestre. Ids soltos (sem FK). */
@Entity
@Table(name = "time_skip_submissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"time_skip_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSkipSubmission {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "time_skip_id", nullable = false)
    private UUID timeSkipId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status;

    @Column(length = 500)
    private String note;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
