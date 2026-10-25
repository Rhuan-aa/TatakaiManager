package com.tatakai.manager.combat.repository;

import com.tatakai.manager.combat.entity.AttackStatus;
import com.tatakai.manager.combat.entity.PendingAttack;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PendingAttackRepository extends JpaRepository<PendingAttack, UUID> {
    List<PendingAttack> findByEncounterIdOrderByCreatedAtDesc(UUID encounterId);

    List<PendingAttack> findByEncounterIdAndStatusOrderByCreatedAtDesc(UUID encounterId, AttackStatus status);

    boolean existsByEncounterIdAndStatus(UUID encounterId, AttackStatus status);

    List<PendingAttack> findByEncounterIdAndAttackerIdAndStatus(UUID encounterId, UUID attackerId, AttackStatus status);
}
