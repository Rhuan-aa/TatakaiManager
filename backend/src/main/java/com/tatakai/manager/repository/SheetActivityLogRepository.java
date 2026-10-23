package com.tatakai.manager.repository;

import com.tatakai.manager.entity.SheetActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SheetActivityLogRepository extends JpaRepository<SheetActivityLog, UUID> {

    List<SheetActivityLog> findBySheetIdOrderByCreatedAtDesc(UUID sheetId);

    List<SheetActivityLog> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId);
}
