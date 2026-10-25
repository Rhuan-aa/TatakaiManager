package com.tatakai.manager.progression.repository;

import com.tatakai.manager.progression.entity.OvercomeDistribution;
import com.tatakai.manager.progression.entity.OvercomeStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OvercomeDistributionRepository extends JpaRepository<OvercomeDistribution, UUID> {
    List<OvercomeDistribution> findBySheetIdOrderByCreatedAtDesc(UUID sheetId);

    boolean existsBySheetIdAndStatus(UUID sheetId, OvercomeStatus status);
}
