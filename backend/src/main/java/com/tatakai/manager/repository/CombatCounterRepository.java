package com.tatakai.manager.repository;

import com.tatakai.manager.entity.CombatCounter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CombatCounterRepository extends JpaRepository<CombatCounter, UUID> {
    List<CombatCounter> findByParticipantIdIn(Collection<UUID> participantIds);
}
