package com.tatakai.manager.auth.dto.response;

import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        String name,
        String email
) {}
