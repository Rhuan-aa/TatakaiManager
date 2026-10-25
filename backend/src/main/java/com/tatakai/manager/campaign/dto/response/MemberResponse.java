package com.tatakai.manager.campaign.dto.response;

import com.tatakai.manager.campaign.entity.Role;

import java.util.UUID;

public record MemberResponse(
        UUID userId,
        String name,
        String email,
        Role role
) {}
