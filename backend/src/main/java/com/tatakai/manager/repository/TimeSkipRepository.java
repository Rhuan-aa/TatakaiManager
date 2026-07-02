package com.tatakai.manager.repository;

import com.tatakai.manager.entity.TimeSkip;
import com.tatakai.manager.entity.TimeSkipStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimeSkipRepository extends JpaRepository<TimeSkip, UUID> {

    List<TimeSkip> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId);

    boolean existsByCampaignIdAndStatus(UUID campaignId, TimeSkipStatus status);
}
