package com.tatakai.manager.npc.dto.response;

import java.util.UUID;

public record CampaignNpcResponse(
        UUID campaignId,
        UUID npcId,
        boolean visible
) {}
