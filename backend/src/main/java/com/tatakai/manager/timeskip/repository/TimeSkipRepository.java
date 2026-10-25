package com.tatakai.manager.timeskip.repository;

import com.tatakai.manager.timeskip.entity.TimeSkip;
import com.tatakai.manager.timeskip.entity.TimeSkipStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimeSkipRepository extends JpaRepository<TimeSkip, UUID> {

    List<TimeSkip> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId);

    boolean existsByCampaignIdAndStatus(UUID campaignId, TimeSkipStatus status);
}
