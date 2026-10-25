package com.tatakai.manager.repository;

import com.tatakai.manager.entity.Encounter;
import com.tatakai.manager.entity.EncounterStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EncounterRepository extends JpaRepository<Encounter, UUID> {
    List<Encounter> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId);

    boolean existsByCampaignIdAndStatusNot(UUID campaignId, EncounterStatus status);
}
