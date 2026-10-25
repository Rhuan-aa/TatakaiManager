package com.tatakai.manager.websocket;

import com.tatakai.manager.dto.response.RollMessage;
import com.tatakai.manager.entity.CampaignMember;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.entity.RollChannel;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CustomRollChannelRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Repassa rolagens somente após o commit: PUBLIC (ou já revelada) vai ao tópico "rolls" da campanha;
 * GM_PRIVATE vai à fila pessoal de cada Mestre (/user/queue/gm-rolls).
 */
@Component
public class RollEventListener {

    public static final String GM_QUEUE = "/queue/gm-rolls";
    public static final String ROLLS_QUEUE = "/queue/rolls";

    private final CampaignEventPublisher publisher;
    private final SimpMessagingTemplate template;
    private final CampaignMemberRepository memberRepository;
    private final CustomRollChannelRepository customChannels;

    public RollEventListener(CampaignEventPublisher publisher, SimpMessagingTemplate template,
                             CampaignMemberRepository memberRepository, CustomRollChannelRepository customChannels) {
        this.publisher = publisher;
        this.template = template;
        this.memberRepository = memberRepository;
        this.customChannels = customChannels;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(RollMessage m) {
        if (m.campaignId() == null) return;
        if (m.channel() == RollChannel.GM_PRIVATE) {
            for (CampaignMember cm : memberRepository.findByCampaignId(m.campaignId())) {
                if (cm.getRole() == Role.MASTER) {
                    template.convertAndSendToUser(cm.getUser().getId().toString(), GM_QUEUE, m);
                }
            }
        }
        if (m.channel() == RollChannel.CUSTOM && m.customChannelId() != null) {
            Set<String> to = new LinkedHashSet<>();
            customChannels.findById(m.customChannelId()).ifPresent(c -> {
                if (c.getMemberIds() != null) c.getMemberIds().forEach(id -> to.add(id.toString()));
            });
            for (CampaignMember cm : memberRepository.findByCampaignId(m.campaignId())) {
                if (cm.getRole() == Role.MASTER) to.add(cm.getUser().getId().toString());
            }
            to.forEach(uid -> template.convertAndSendToUser(uid, ROLLS_QUEUE, m));
        }
        if (m.channel() == RollChannel.PUBLIC || m.revealed()) {
            publisher.publish(m.campaignId(), "rolls", m);
        }
    }
}
