package com.tatakai.manager.repository;

import com.tatakai.manager.entity.TrainingEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrainingEntryRepository extends JpaRepository<TrainingEntry, UUID> {
    List<TrainingEntry> findBySheetIdOrderByCreatedAt(UUID sheetId);

    List<TrainingEntry> findBySheetIdAndTimeSkipId(UUID sheetId, UUID timeSkipId);

    long countBySheetIdAndTarget(UUID sheetId, String target);
}
