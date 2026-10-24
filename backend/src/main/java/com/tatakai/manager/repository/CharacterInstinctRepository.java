package com.tatakai.manager.repository;

import com.tatakai.manager.entity.CharacterInstinct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CharacterInstinctRepository extends JpaRepository<CharacterInstinct, UUID> {
    List<CharacterInstinct> findBySheetIdOrderByCreatedAt(UUID sheetId);
}
