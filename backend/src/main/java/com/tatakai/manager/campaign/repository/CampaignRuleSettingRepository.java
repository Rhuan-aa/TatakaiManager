package com.tatakai.manager.campaign.repository;

import com.tatakai.manager.campaign.entity.CampaignRuleSetting;
import com.tatakai.manager.campaign.entity.OptionalRule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignRuleSettingRepository extends JpaRepository<CampaignRuleSetting, UUID> {
    List<CampaignRuleSetting> findByCampaignId(UUID campaignId);

    Optional<CampaignRuleSetting> findByCampaignIdAndRuleKey(UUID campaignId, OptionalRule ruleKey);
}
