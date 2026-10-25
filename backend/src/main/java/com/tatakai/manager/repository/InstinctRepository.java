package com.tatakai.manager.repository;

import com.tatakai.manager.entity.Instinct;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface InstinctRepository extends MongoRepository<Instinct, UUID> {
    List<Instinct> findByCampaignIdIsNull();

    /** Fallback da importação: o catálogo reimportado troca os ids, mas mantém o nome. */
    java.util.Optional<Instinct> findFirstByName(String name);

    List<Instinct> findByCampaignId(UUID campaignId);
}
