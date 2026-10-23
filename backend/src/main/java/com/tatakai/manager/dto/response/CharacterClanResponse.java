package com.tatakai.manager.dto.response;

import java.util.List;
import java.util.UUID;

public record CharacterClanResponse(
        UUID id, UUID clanId, String name, List<String> heritage
) {}
