package com.tatakai.manager.repository;

import com.tatakai.manager.entity.Forge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForgeRepository extends JpaRepository<Forge, UUID> {
    Optional<Forge> findBySheetIdAndCompletedAtIsNull(UUID sheetId);
}
