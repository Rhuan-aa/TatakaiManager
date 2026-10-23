package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.OptionalRule;

import java.util.Map;

public record CampaignRuleResponse(
        OptionalRule ruleKey, boolean enabled, Map<String, String> params
) {}
