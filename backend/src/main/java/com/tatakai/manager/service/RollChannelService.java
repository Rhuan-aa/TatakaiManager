package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.RollChannelRequest;
import com.tatakai.manager.dto.response.RollChannelResponse;
import com.tatakai.manager.entity.CampaignMember;
import com.tatakai.manager.entity.CustomRollChannel;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.InvalidRollException;
import com.tatakai.manager.exception.RollChannelNameTakenException;
import com.tatakai.manager.exception.RollNotFoundException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CustomRollChannelRepository;
import com.tatakai.manager.repository.RollRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** CRUD dos canais de rolagem personalizados (escrita só do Mestre). */
@Service
public class RollChannelService {

    private final CustomRollChannelRepository channels;
    private final CampaignMemberRepository memberRepository;
    private final RollRepository rollRepository;

    public RollChannelService(CustomRollChannelRepository channels, CampaignMemberRepository memberRepository,
                              RollRepository rollRepository) {
        this.channels = channels;
        this.memberRepository = memberRepository;
        this.rollRepository = rollRepository;
    }

    private CampaignMember requireMembership(UUID campaignId, UUID userId) {
        return memberRepository.findByCampaignIdAndUserId(campaignId, userId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"));
    }

    private void requireMaster(UUID campaignId, UUID userId) {
        if (requireMembership(campaignId, userId).getRole() != Role.MASTER) {
            throw new AccessDeniedException("Apenas o Mestre pode gerenciar canais");
        }
    }

    @Transactional(readOnly = true)
    public List<RollChannelResponse> list(UUID campaignId, UUID userId) {
        boolean master = requireMembership(campaignId, userId).getRole() == Role.MASTER;
        return channels.findByCampaignIdOrderByCreatedAtAsc(campaignId).stream()
                .filter(c -> master || c.getMemberIds() != null && c.getMemberIds().contains(userId))
                .map(RollChannelService::toResponse).toList();
    }

    @Transactional
    public RollChannelResponse create(UUID campaignId, RollChannelRequest req, UUID userId) {
        requireMaster(campaignId, userId);
        List<UUID> members = validMembers(campaignId, req);
        String name = req.name().trim();
        channels.findByCampaignIdAndNameIgnoreCase(campaignId, name).ifPresent(c -> {
            throw new RollChannelNameTakenException();
        });
        return toResponse(channels.save(CustomRollChannel.builder()
                .campaignId(campaignId).name(name).memberIds(members).build()));
    }

    @Transactional
    public RollChannelResponse update(UUID campaignId, UUID channelId, RollChannelRequest req, UUID userId) {
        requireMaster(campaignId, userId);
        CustomRollChannel c = load(campaignId, channelId);
        List<UUID> members = validMembers(campaignId, req);
        String name = req.name().trim();
        channels.findByCampaignIdAndNameIgnoreCase(campaignId, name).filter(o -> !o.getId().equals(c.getId()))
                .ifPresent(o -> {
                    throw new RollChannelNameTakenException();
                });
        c.setName(name);
        c.setMemberIds(members);
        return toResponse(channels.save(c));
    }

    /** Apaga o canal e as rolagens dele. */
    @Transactional
    public void delete(UUID campaignId, UUID channelId, UUID userId) {
        requireMaster(campaignId, userId);
        CustomRollChannel c = load(campaignId, channelId);
        rollRepository.deleteByCustomChannelId(c.getId());
        channels.delete(c);
    }

    private CustomRollChannel load(UUID campaignId, UUID channelId) {
        return channels.findById(channelId).filter(c -> campaignId.equals(c.getCampaignId()))
                .orElseThrow(() -> new RollNotFoundException("Canal não encontrado"));
    }

    private List<UUID> validMembers(UUID campaignId, RollChannelRequest req) {
        List<UUID> ids = req.memberIds() == null ? List.of() : req.memberIds().stream().distinct().toList();
        for (UUID id : ids) {
            if (!memberRepository.existsByCampaignIdAndUserId(campaignId, id)) {
                throw new InvalidRollException("Todos os participantes do canal devem ser membros da campanha");
            }
        }
        return ids;
    }

    static RollChannelResponse toResponse(CustomRollChannel c) {
        return new RollChannelResponse(c.getId(), c.getCampaignId(), c.getName(),
                c.getMemberIds() == null ? List.of() : c.getMemberIds(), c.getCreatedAt());
    }
}
