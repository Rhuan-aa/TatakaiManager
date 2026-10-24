package com.tatakai.manager.repository;

import com.tatakai.manager.entity.CharacterArt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CharacterArtRepository extends JpaRepository<CharacterArt, UUID> {
    List<CharacterArt> findBySheetIdOrderByCreatedAt(UUID sheetId);

    boolean existsBySheetIdAndArtId(UUID sheetId, UUID artId);
}
