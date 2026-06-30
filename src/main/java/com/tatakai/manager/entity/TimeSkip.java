package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "time_skips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSkip {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "total_days", nullable = false)
    private short totalDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TimeSkipStatus status = TimeSkipStatus.ACTIVE;

    @OneToMany(mappedBy = "timeSkip", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TimeSkipDay> days = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    /** Gera os dias 1..totalDays vinculados a este TimeSkip. */
    public void generateDays() {
        days.clear();
        for (short d = 1; d <= totalDays; d++) {
            days.add(TimeSkipDay.builder().timeSkip(this).dayNumber(d).build());
        }
    }
}
