package com.tatakai.manager.websocket;

import com.tatakai.manager.dto.response.SlotUpdateMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publica eventos de mudança de slot no canal STOMP da campanha.
 * Encapsula o SimpMessagingTemplate para manter o BookingService testável.
 */
@Component
public class SlotEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public SlotEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(SlotUpdateMessage message) {
        messagingTemplate.convertAndSend(destination(message.campaignId()), message);
    }

    public static String destination(UUID campaignId) {
        return "/topic/campaign/" + campaignId + "/slots";
    }
}
