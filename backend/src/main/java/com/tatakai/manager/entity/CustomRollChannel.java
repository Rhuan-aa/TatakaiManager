package com.tatakai.manager.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Canal de rolagem personalizado de uma campanha (aba): visível/rolável só para os membros listados e o Mestre. */
@Entity
@Table(name = "roll_channels", uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomRollChannel {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(nullable = false, length = 50)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "member_ids", columnDefinition = "jsonb")
    private List<UUID> memberIds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
