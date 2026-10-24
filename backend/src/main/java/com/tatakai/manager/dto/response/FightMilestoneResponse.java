package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.MilestoneCategory;

import java.util.UUID;

public record FightMilestoneResponse(UUID id, UUID campaignId, String name, MilestoneCategory category,
                                     String description, String autoTrigger, boolean grantsArtPoint,
                                     boolean seriousOnly, Integer minOtherDisadvantage) {}
