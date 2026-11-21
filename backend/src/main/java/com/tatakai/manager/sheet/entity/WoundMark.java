package com.tatakai.manager.sheet.entity;

import com.tatakai.manager.sheet.dto.request.WoundGeometry;
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

/** Ferimento desenhado no boneco (§53.3). Só visual: o efeito mecânico vem do {@link PermanentInjury}. */
@Entity
@Table(name = "wound_marks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WoundMark {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CharacterSheet sheet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BodyPart part;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WoundType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private WoundGeometry geometry;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private InjurySeverity severity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
