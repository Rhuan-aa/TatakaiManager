package com.tatakai.manager.npc.repository;

import com.tatakai.manager.npc.entity.NpcImage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NpcImageRepository extends JpaRepository<NpcImage, UUID> {
}
