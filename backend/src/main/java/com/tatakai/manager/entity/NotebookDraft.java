package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Rascunho do Caderno Marcial (spec §9.3): técnica ou base montada, aprovada pelo Mestre antes da Forja. */
@Entity
@Table(name = "notebook_drafts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotebookDraft {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CharacterSheet sheet;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private MoveKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private ArtTechnique technique;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private ArtBase base;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private NotebookStatus status;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getName() {
        return kind == MoveKind.BASE ? (base == null ? null : base.getName())
                : (technique == null ? null : technique.getName());
    }

    public Graduation getGraduation() {
        Graduation g = kind == MoveKind.BASE ? (base == null ? null : base.getGraduation())
                : (technique == null ? null : technique.getGraduation());
        return g == null ? Graduation.GENERIC : g;
    }
}
