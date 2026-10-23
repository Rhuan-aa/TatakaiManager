package com.tatakai.manager.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CampaignEventPublisher — Sprint 9 — F1 Ficha (NFR-03)")
class CampaignEventPublisherTest {

    @Mock private SimpMessagingTemplate template;

    @Test
    @DisplayName("destination() monta /topic/campaign/{id}/{canal}")
    void destination_format() {
        UUID id = UUID.randomUUID();
        assertThat(CampaignEventPublisher.destination(id, "sheets"))
                .isEqualTo("/topic/campaign/" + id + "/sheets");
    }

    @Test
    @DisplayName("publish() envia o payload ao destino da campanha/canal")
    void publish_sendsToDestination() {
        UUID id = UUID.randomUUID();
        Object payload = new Object();

        new CampaignEventPublisher(template).publish(id, "sheets", payload);

        verify(template).convertAndSend("/topic/campaign/" + id + "/sheets", payload);
    }
}
