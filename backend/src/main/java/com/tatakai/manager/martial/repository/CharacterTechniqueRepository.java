package com.tatakai.manager.martial.repository;

import com.tatakai.manager.martial.entity.CharacterTechnique;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CharacterTechniqueRepository extends JpaRepository<CharacterTechnique, UUID> {
    List<CharacterTechnique> findBySheetIdOrderByCreatedAt(UUID sheetId);

    List<CharacterTechnique> findByCharacterArtId(UUID characterArtId);
}
