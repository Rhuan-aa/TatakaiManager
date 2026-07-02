package com.tatakai.manager.dto.response;

import java.util.UUID;

/**
 * Mensagem de tempo real enviada via STOMP quando um slot muda de estado.
 * Canal: /topic/campaign/{campaignId}/slots
 */
public record SlotUpdateMessage(
        SlotEvent event,
        UUID campaignId,
        UUID timeSkipId,
        UUID npcId,
        short dayNumber,
        short slotNumber,
        UUID userId,
        String userName,
        String interactionName,
        Short idlePointCost
) {
    public enum SlotEvent {
        BOOKED,
        CANCELLED
    }

    public static SlotUpdateMessage booked(UUID campaignId, BookingResponse b, UUID timeSkipId) {
        return new SlotUpdateMessage(SlotEvent.BOOKED, campaignId, timeSkipId, b.npcId(),
                b.dayNumber(), b.slotNumber(), b.userId(), b.userName(),
                b.interactionName(), b.idlePointCost());
    }

    public static SlotUpdateMessage cancelled(UUID campaignId, UUID timeSkipId, UUID npcId,
                                              short dayNumber, short slotNumber) {
        return new SlotUpdateMessage(SlotEvent.CANCELLED, campaignId, timeSkipId, npcId,
                dayNumber, slotNumber, null, null, null, null);
    }
}
