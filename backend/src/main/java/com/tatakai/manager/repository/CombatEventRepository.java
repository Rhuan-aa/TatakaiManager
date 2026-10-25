package com.tatakai.manager.repository;

import com.tatakai.manager.entity.CombatEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CombatEventRepository extends JpaRepository<CombatEvent, UUID> {
    List<CombatEvent> findByEncounterIdOrderByCreatedAtAsc(UUID encounterId);
}
