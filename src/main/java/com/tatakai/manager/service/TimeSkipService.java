package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.CreateTimeSkipRequest;
import com.tatakai.manager.dto.response.TimeSkipResponse;
import com.tatakai.manager.entity.Campaign;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.entity.TimeSkip;
import com.tatakai.manager.entity.TimeSkipStatus;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.ActiveTimeSkipExistsException;
import com.tatakai.manager.exception.CampaignNotFoundException;
import com.tatakai.manager.exception.TimeSkipClosedException;
import com.tatakai.manager.exception.TimeSkipNotFoundException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CampaignRepository;
import com.tatakai.manager.repository.TimeSkipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TimeSkipService {

    private final TimeSkipRepository timeSkipRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignMemberRepository memberRepository;

    public TimeSkipService(TimeSkipRepository timeSkipRepository,
                           CampaignRepository campaignRepository,
                           CampaignMemberRepository memberRepository) {
        this.timeSkipRepository = timeSkipRepository;
        this.campaignRepository = campaignRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public TimeSkipResponse create(UUID campaignId, UUID requesterId, CreateTimeSkipRequest req) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(CampaignNotFoundException::new);
        requireCampaignMaster(campaignId, requesterId);

        // Apenas um TimeSkip ativo por campanha
        if (timeSkipRepository.existsByCampaignIdAndStatus(campaignId, TimeSkipStatus.ACTIVE)) {
            throw new ActiveTimeSkipExistsException();
        }

        TimeSkip timeSkip = TimeSkip.builder()
                .campaign(campaign)
                .name(req.name())
                .totalDays(req.totalDays())
                .status(TimeSkipStatus.ACTIVE)
                .build();
        timeSkip.generateDays();

        return toResponse(timeSkipRepository.save(timeSkip));
    }

    @Transactional
    public TimeSkipResponse close(UUID timeSkipId, UUID requesterId) {
        TimeSkip timeSkip = timeSkipRepository.findById(timeSkipId)
                .orElseThrow(TimeSkipNotFoundException::new);
        requireCampaignMaster(timeSkip.getCampaign().getId(), requesterId);

        if (timeSkip.getStatus() == TimeSkipStatus.CLOSED) {
            throw new TimeSkipClosedException();
        }

        timeSkip.setStatus(TimeSkipStatus.CLOSED);
        timeSkip.setClosedAt(Instant.now());
        return toResponse(timeSkipRepository.save(timeSkip));
    }

    @Transactional(readOnly = true)
    public List<TimeSkipResponse> listForCampaign(UUID campaignId, UUID requesterId) {
        if (!memberRepository.existsByCampaignIdAndUserId(campaignId, requesterId)) {
            throw new AccessDeniedException("Você não pertence a esta campanha");
        }
        return timeSkipRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream()
                .map(this::toResponse)
                .toList();
    }

    private void requireCampaignMaster(UUID campaignId, UUID requesterId) {
        if (!memberRepository.existsByCampaignIdAndUserIdAndRole(campaignId, requesterId, Role.MASTER)) {
            throw new AccessDeniedException("Apenas o Mestre da campanha pode realizar esta ação");
        }
    }

    private TimeSkipResponse toResponse(TimeSkip ts) {
        return new TimeSkipResponse(
                ts.getId(),
                ts.getCampaign().getId(),
                ts.getName(),
                ts.getTotalDays(),
                ts.getStatus(),
                ts.getCreatedAt(),
                ts.getClosedAt());
    }
}
