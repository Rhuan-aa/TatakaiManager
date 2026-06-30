package com.tatakai.manager.repository;

import com.tatakai.manager.entity.CampaignNpc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignNpcRepository extends JpaRepository<CampaignNpc, UUID> {

    List<CampaignNpc> findByCampaignId(UUID campaignId);

    List<CampaignNpc> findByCampaignIdAndVisibleTrue(UUID campaignId);

    Optional<CampaignNpc> findByCampaignIdAndNpcId(UUID campaignId, UUID npcId);

    boolean existsByCampaignIdAndNpcId(UUID campaignId, UUID npcId);
}
