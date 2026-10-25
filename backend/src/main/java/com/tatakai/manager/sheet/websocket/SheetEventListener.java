package com.tatakai.manager.sheet.websocket;

import com.tatakai.manager.shared.websocket.CampaignEventPublisher;
import com.tatakai.manager.sheet.dto.response.SheetUpdateMessage;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Repassa eventos de ficha ao tópico da campanha somente após o commit. */
@Component
public class SheetEventListener {

    private final CampaignEventPublisher publisher;

    public SheetEventListener(CampaignEventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(SheetUpdateMessage m) {
        publisher.publish(m.campaignId(), "sheets", m);
    }
}
