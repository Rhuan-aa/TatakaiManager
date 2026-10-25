package com.tatakai.manager.npc.repository;

import com.tatakai.manager.npc.entity.Npc;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface NpcRepository extends MongoRepository<Npc, UUID> {
    List<Npc> findByOwnerId(UUID ownerId);
}
