package com.tatakai.manager.sheet.entity;

import com.tatakai.manager.sheet.dto.request.GmAdjustmentRequest;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pacote de ajustes (os mesmos do Mestre) que o dono propõe numa ficha pronta; só vale depois que o Mestre aprova. */
@Entity
@Table(name = "sheet_change_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SheetChangeRequest {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "sheet_id", nullable = false)
    private UUID sheetId;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SheetChangeStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<GmAdjustmentRequest> changes = new ArrayList<>();

    /** Resumo congelado na decisão (linhas separadas por \n); pendente = calculado na hora. */
    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
