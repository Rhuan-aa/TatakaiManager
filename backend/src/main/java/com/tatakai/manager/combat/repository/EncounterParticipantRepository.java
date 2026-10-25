package com.tatakai.manager.combat.repository;

import com.tatakai.manager.combat.entity.EncounterParticipant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EncounterParticipantRepository extends JpaRepository<EncounterParticipant, UUID> {
    List<EncounterParticipant> findByEncounterIdOrderByOrderIndex(UUID encounterId);

    boolean existsByEncounterIdAndSheetId(UUID encounterId, UUID sheetId);
}
