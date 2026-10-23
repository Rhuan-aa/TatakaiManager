package com.tatakai.manager.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Publicador genérico de eventos no tópico STOMP de uma campanha. */
@Component
public class CampaignEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public CampaignEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(UUID campaignId, String channel, Object payload) {
        messagingTemplate.convertAndSend(destination(campaignId, channel), payload);
    }

    public static String destination(UUID campaignId, String channel) {
        return "/topic/campaign/" + campaignId + "/" + channel;
    }
}
