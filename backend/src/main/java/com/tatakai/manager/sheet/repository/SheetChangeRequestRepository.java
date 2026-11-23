package com.tatakai.manager.sheet.repository;

import com.tatakai.manager.sheet.entity.SheetChangeRequest;
import com.tatakai.manager.sheet.entity.SheetChangeStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SheetChangeRequestRepository extends JpaRepository<SheetChangeRequest, UUID> {

    List<SheetChangeRequest> findBySheetIdOrderByCreatedAtDesc(UUID sheetId);

    boolean existsBySheetIdAndStatus(UUID sheetId, SheetChangeStatus status);
}
