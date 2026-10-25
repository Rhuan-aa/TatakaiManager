package com.tatakai.manager.repository;

import com.tatakai.manager.entity.EncounterParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EncounterParticipantRepository extends JpaRepository<EncounterParticipant, UUID> {
    List<EncounterParticipant> findByEncounterIdOrderByOrderIndex(UUID encounterId);

    boolean existsByEncounterIdAndSheetId(UUID encounterId, UUID sheetId);
}
