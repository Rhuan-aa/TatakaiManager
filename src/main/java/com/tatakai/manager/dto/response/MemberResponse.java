package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.Role;

import java.util.UUID;

public record MemberResponse(
        UUID userId,
        String name,
        String email,
        Role role
) {}
