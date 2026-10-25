package com.tatakai.manager.campaign.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record UpdateCampaignRuleRequest(
        @NotNull Boolean enabled,
        Map<String, String> params
) {}
