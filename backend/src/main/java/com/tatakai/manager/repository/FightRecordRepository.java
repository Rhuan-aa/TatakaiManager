package com.tatakai.manager.repository;

import com.tatakai.manager.entity.FightClassification;
import com.tatakai.manager.entity.FightRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FightRecordRepository extends JpaRepository<FightRecord, UUID> {
    List<FightRecord> findBySheetIdOrderByCreatedAtDesc(UUID sheetId);

    boolean existsBySheetIdAndClassificationAndTimeSkipIdAndDayNumber(UUID sheetId, FightClassification c,
                                                                      UUID timeSkipId, Short dayNumber);

    boolean existsBySheetIdAndClassificationAndTimeSkipIdIsNullAndFightDate(UUID sheetId, FightClassification c,
                                                                            LocalDate date);
}
