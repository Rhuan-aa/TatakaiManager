package com.tatakai.manager.build.repository;

import com.tatakai.manager.build.entity.CharacterBuild;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CharacterBuildRepository extends JpaRepository<CharacterBuild, UUID> {
}
