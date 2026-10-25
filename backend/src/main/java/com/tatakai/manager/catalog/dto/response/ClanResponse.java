package com.tatakai.manager.catalog.dto.response;

import com.tatakai.manager.build.entity.BuildGains;

import java.util.List;
import java.util.UUID;

public record ClanResponse(
        UUID id, UUID campaignId, String name, Integer rollNumber, String description,
        BuildGains buildGains, List<String> heritage, int version
) {}
