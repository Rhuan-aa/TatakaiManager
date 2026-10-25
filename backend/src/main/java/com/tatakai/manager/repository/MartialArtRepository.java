package com.tatakai.manager.repository;

import com.tatakai.manager.entity.MartialArt;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface MartialArtRepository extends MongoRepository<MartialArt, UUID> {
    List<MartialArt> findByCampaignIdIsNull();

    /** Fallback da importação: o catálogo reimportado troca os ids, mas mantém o nome. */
    java.util.Optional<MartialArt> findFirstByName(String name);

    List<MartialArt> findByCampaignId(UUID campaignId);
}
