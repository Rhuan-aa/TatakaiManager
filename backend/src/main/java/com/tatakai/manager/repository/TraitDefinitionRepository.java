package com.tatakai.manager.repository;

import com.tatakai.manager.entity.TraitDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface TraitDefinitionRepository extends MongoRepository<TraitDefinition, UUID> {
    List<TraitDefinition> findByCampaignIdIsNull();

    /** Fallback da importação: o catálogo reimportado troca os ids, mas mantém o nome. */
    java.util.Optional<TraitDefinition> findFirstByName(String name);

    List<TraitDefinition> findByCampaignId(UUID campaignId);
}
