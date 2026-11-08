package com.tatakai.manager.martial.repository;

import com.tatakai.manager.martial.entity.LooseMove;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface LooseMoveRepository extends MongoRepository<LooseMove, UUID> {

    List<LooseMove> findByCampaignIdOrderByNameAsc(UUID campaignId);
}
