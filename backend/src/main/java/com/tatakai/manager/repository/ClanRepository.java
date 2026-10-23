package com.tatakai.manager.repository;

import com.tatakai.manager.entity.Clan;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface ClanRepository extends MongoRepository<Clan, UUID> {
    List<Clan> findByCampaignIdIsNull();

    List<Clan> findByCampaignId(UUID campaignId);
}
