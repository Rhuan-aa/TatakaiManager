package com.tatakai.manager.campaign.websocket;

import com.tatakai.manager.campaign.dto.response.CampaignUpdateMessage;
import com.tatakai.manager.shared.websocket.CampaignEventPublisher;
import com.tatakai.manager.timeskip.entity.TimeSkip;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/** Publica no tópico `/topic/campaign/{id}/campaign` depois do commit (TimeSkip e NPCs). */
@Component
public class CampaignUpdateListener {

    private final CampaignEventPublisher publisher;

    public CampaignUpdateListener(CampaignEventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CampaignUpdateMessage m) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", m.type());
        body.put("id", m.id());
        publisher.publish(m.campaignId(), "campaign", body);
    }
}
