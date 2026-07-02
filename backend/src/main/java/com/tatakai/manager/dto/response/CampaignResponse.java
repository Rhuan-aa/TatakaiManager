package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.Role;

import java.util.UUID;

public record CampaignResponse(
        UUID id,
        String name,
        String description,
        Role currentUserRole
) {}
