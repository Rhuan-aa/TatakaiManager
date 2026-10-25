package com.tatakai.manager.combat.repository;

import com.tatakai.manager.combat.entity.AttackTarget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AttackTargetRepository extends JpaRepository<AttackTarget, UUID> {
    List<AttackTarget> findByAttackIdIn(Collection<UUID> attackIds);
}
