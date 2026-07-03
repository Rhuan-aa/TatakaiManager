package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.SoloActivityType;

import java.util.UUID;

/**
 * Mensagem de tempo real enviada via STOMP quando um slot muda de estado.
 * Canal: /topic/campaign/{campaignId}/slots
 */
public record SlotUpdateMessage(
        SlotEvent event,
        UUID campaignId,
        UUID timeSkipId,
        UUID bookingId,
        UUID npcId,
        short dayNumber,
        short slotNumber,
        UUID userId,
        String userName,
        String interactionName,
        Short idlePointCost,
        SoloActivityType soloActivityType,
        String description
) {
    public enum SlotEvent {
        BOOKED,
        CANCELLED
    }

    public static SlotUpdateMessage booked(UUID campaignId, BookingResponse b, UUID timeSkipId) {
        return new SlotUpdateMessage(SlotEvent.BOOKED, campaignId, timeSkipId, b.id(), b.npcId(),
                b.dayNumber(), b.slotNumber(), b.userId(), b.userName(),
                b.interactionName(), b.idlePointCost(), b.soloActivityType(), b.description());
    }

    public static SlotUpdateMessage cancelled(UUID campaignId, UUID timeSkipId, UUID bookingId,
                                              UUID npcId, short dayNumber, short slotNumber) {
        return new SlotUpdateMessage(SlotEvent.CANCELLED, campaignId, timeSkipId, bookingId, npcId,
                dayNumber, slotNumber, null, null, null, null, null, null);
    }
}
