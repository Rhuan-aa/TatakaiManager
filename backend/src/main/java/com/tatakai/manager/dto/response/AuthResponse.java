package com.tatakai.manager.dto.response;

import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        String name,
        String email
) {}
