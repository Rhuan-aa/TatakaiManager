package com.tatakai.manager.campaign.dto.response;

import com.tatakai.manager.campaign.entity.Role;

import java.util.UUID;

public record CampaignResponse(
        UUID id,
        String name,
        String description,
        Role currentUserRole
) {}
