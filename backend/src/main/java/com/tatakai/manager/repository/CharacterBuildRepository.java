package com.tatakai.manager.repository;

import com.tatakai.manager.entity.CharacterBuild;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CharacterBuildRepository extends JpaRepository<CharacterBuild, UUID> {
}
