package com.tatakai.manager.campaign.repository;

import com.tatakai.manager.campaign.entity.CampaignMember;
import com.tatakai.manager.campaign.entity.Role;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignMemberRepository extends JpaRepository<CampaignMember, UUID> {

    List<CampaignMember> findByUserId(UUID userId);

    List<CampaignMember> findByCampaignId(UUID campaignId);

    Optional<CampaignMember> findByCampaignIdAndUserId(UUID campaignId, UUID userId);

    boolean existsByCampaignIdAndUserId(UUID campaignId, UUID userId);

    boolean existsByCampaignIdAndUserIdAndRole(UUID campaignId, UUID userId, Role role);
}
