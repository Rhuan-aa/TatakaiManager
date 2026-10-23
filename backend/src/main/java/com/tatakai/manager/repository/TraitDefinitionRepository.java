package com.tatakai.manager.repository;

import com.tatakai.manager.entity.TraitDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface TraitDefinitionRepository extends MongoRepository<TraitDefinition, UUID> {
    List<TraitDefinition> findByCampaignIdIsNull();

    List<TraitDefinition> findByCampaignId(UUID campaignId);
}
