package com.tatakai.manager.repository;

import com.tatakai.manager.entity.Rival;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RivalRepository extends JpaRepository<Rival, UUID> {
    List<Rival> findBySheetIdOrderByName(UUID sheetId);

    Optional<Rival> findBySheetIdAndName(UUID sheetId, String name);
}
