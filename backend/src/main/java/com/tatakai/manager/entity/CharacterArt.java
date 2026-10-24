package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Arte marcial praticada por uma ficha (snapshot do nome e da categoria do catálogo). */
@Entity
@Table(name = "character_arts", uniqueConstraints = @UniqueConstraint(columnNames = {"sheet_id", "art_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterArt {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CharacterSheet sheet;

    @Column(name = "art_id", nullable = false)
    private UUID artId;

    @Column(name = "art_name", length = 100)
    private String artName;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ArtCategory category;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Graduation graduation;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CharacterArtStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
