package com.tatakai.manager.repository;

import com.tatakai.manager.entity.Npc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface NpcRepository extends MongoRepository<Npc, UUID> {
    List<Npc> findByOwnerId(UUID ownerId);
}
