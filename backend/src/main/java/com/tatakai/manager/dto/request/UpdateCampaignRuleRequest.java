package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record UpdateCampaignRuleRequest(
        @NotNull Boolean enabled,
        Map<String, String> params
) {}
