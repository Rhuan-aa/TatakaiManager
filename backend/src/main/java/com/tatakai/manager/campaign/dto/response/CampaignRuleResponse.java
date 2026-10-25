package com.tatakai.manager.campaign.dto.response;

import com.tatakai.manager.campaign.entity.OptionalRule;

import java.util.Map;

public record CampaignRuleResponse(
        OptionalRule ruleKey, boolean enabled, Map<String, String> params
) {}
