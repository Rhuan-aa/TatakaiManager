package com.tatakai.manager.catalog.repository;

import com.tatakai.manager.catalog.entity.Clan;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface ClanRepository extends MongoRepository<Clan, UUID> {
    List<Clan> findByCampaignIdIsNull();

    /** Fallback da importação: o catálogo reimportado troca os ids, mas mantém o nome. */
    java.util.Optional<Clan> findFirstByName(String name);

    List<Clan> findByCampaignId(UUID campaignId);
}
