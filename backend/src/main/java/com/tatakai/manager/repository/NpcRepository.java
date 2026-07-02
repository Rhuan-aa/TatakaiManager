package com.tatakai.manager.repository;

import com.tatakai.manager.entity.Npc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NpcRepository extends JpaRepository<Npc, UUID> {
    List<Npc> findByOwnerId(UUID ownerId);
}
