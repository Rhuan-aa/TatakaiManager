package com.tatakai.manager.repository;

import com.tatakai.manager.entity.CustomRollChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomRollChannelRepository extends JpaRepository<CustomRollChannel, UUID> {
    List<CustomRollChannel> findByCampaignIdOrderByCreatedAtAsc(UUID campaignId);

    Optional<CustomRollChannel> findByCampaignIdAndNameIgnoreCase(UUID campaignId, String name);
}
