package com.tatakai.manager.dto.response;

import java.util.UUID;

public record CampaignNpcResponse(
        UUID campaignId,
        UUID npcId,
        boolean visible
) {}
