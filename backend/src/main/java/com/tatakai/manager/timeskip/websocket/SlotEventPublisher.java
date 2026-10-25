package com.tatakai.manager.timeskip.websocket;

import com.tatakai.manager.shared.websocket.CampaignEventPublisher;
import com.tatakai.manager.timeskip.dto.response.SlotUpdateMessage;
import com.tatakai.manager.timeskip.service.BookingService;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publica eventos de mudança de slot no canal STOMP da campanha.
 * Encapsula o publisher genérico para manter o BookingService testável.
 */
@Component
public class SlotEventPublisher {

    private static final String CHANNEL = "slots";

    private final CampaignEventPublisher publisher;

    public SlotEventPublisher(CampaignEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(SlotUpdateMessage message) {
        publisher.publish(message.campaignId(), CHANNEL, message);
    }

    public static String destination(UUID campaignId) {
        return CampaignEventPublisher.destination(campaignId, CHANNEL);
    }
}
