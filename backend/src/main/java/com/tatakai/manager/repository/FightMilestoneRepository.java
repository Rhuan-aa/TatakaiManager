package com.tatakai.manager.repository;

import com.tatakai.manager.entity.FightMilestone;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface FightMilestoneRepository extends MongoRepository<FightMilestone, UUID> {
    List<FightMilestone> findByCampaignIdIsNull();

    List<FightMilestone> findByCampaignId(UUID campaignId);
}
