package com.tatakai.manager.repository;

import com.tatakai.manager.entity.InteractionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InteractionLogRepository extends JpaRepository<InteractionLog, UUID> {

    List<InteractionLog> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId);
}
