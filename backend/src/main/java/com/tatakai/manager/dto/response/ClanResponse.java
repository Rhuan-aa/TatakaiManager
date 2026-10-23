package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.BuildGains;

import java.util.List;
import java.util.UUID;

public record ClanResponse(
        UUID id, UUID campaignId, String name, Integer rollNumber, String description,
        BuildGains buildGains, List<String> heritage, int version
) {}
