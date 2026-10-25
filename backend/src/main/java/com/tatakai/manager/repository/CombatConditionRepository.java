package com.tatakai.manager.repository;

import com.tatakai.manager.entity.CombatCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CombatConditionRepository extends JpaRepository<CombatCondition, UUID> {
    List<CombatCondition> findByParticipantIdIn(Collection<UUID> participantIds);
}
