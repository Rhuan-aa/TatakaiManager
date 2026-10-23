package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Lesão permanente de uma ficha digital. */
@Entity
@Table(name = "permanent_injuries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermanentInjury {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CharacterSheet sheet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InjuryType type;

    @Column(length = 100)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InjurySeverity severity;

    private int hpMaxReduction;
    private int limiterReduction;

    @Enumerated(EnumType.STRING)
    @Column(name = "affected_attribute", length = 20)
    private Attribute affectedAttribute;

    private boolean treated;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
