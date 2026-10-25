package com.tatakai.manager.combat.websocket;

import com.tatakai.manager.combat.dto.response.CombatUpdateMessage;
import com.tatakai.manager.shared.websocket.CampaignEventPublisher;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/** Avisa a campanha (após o commit) que o encontro mudou; o cliente refaz o GET. */
@Component
public class CombatEventListener {

    private final CampaignEventPublisher publisher;

    public CombatEventListener(CampaignEventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CombatUpdateMessage m) {
        publisher.publish(m.campaignId(), "combat", Map.of("encounterId", m.encounterId(), "type", m.type()));
    }
}
