package com.tatakai.manager.booking;

import com.tatakai.manager.dto.response.SlotUpdateMessage;
import com.tatakai.manager.websocket.SlotEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.tatakai.manager.websocket.CampaignEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SlotEventPublisher — Sprint 6 (US-15)")
class SlotEventPublisherTest {

    @Mock private CampaignEventPublisher campaignPublisher;

    @Test
    @DisplayName("publica no canal \"slots\" do publisher genérico")
    void publish_sendsToCampaignChannel() {
        var publisher = new SlotEventPublisher(campaignPublisher);
        UUID campaignId = UUID.randomUUID();
        var message = new SlotUpdateMessage(
                SlotUpdateMessage.SlotEvent.BOOKED, campaignId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), (short) 3, (short) 2, UUID.randomUUID(), "Ana",
                "Treino", (short) 2, null, null, null, null);

        publisher.publish(message);

        verify(campaignPublisher).publish(campaignId, "slots", message);
    }

    @Test
    @DisplayName("destination() monta o canal correto da campanha")
    void destination_buildsChannel() {
        UUID campaignId = UUID.randomUUID();
        assertThat(SlotEventPublisher.destination(campaignId))
                .isEqualTo("/topic/campaign/" + campaignId + "/slots");
    }
}
