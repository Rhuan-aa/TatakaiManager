package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.TimeSkipActivityRequest;
import com.tatakai.manager.dto.response.TimeSkipActivityResponse;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.entity.TimeSkip;
import com.tatakai.manager.entity.TimeSkipActivity;
import com.tatakai.manager.entity.TimeSkipStatus;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.InvalidActivityNameException;
import com.tatakai.manager.exception.TimeSkipActivityNotFoundException;
import com.tatakai.manager.exception.TimeSkipClosedException;
import com.tatakai.manager.exception.TimeSkipNotFoundException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.TimeSkipActivityRepository;
import com.tatakai.manager.repository.TimeSkipRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Catálogo de atividades solo exclusivas de um TimeSkip (ex.: "Reconstrução da
 * vila"), cadastrado pelo Mestre — ao contrário de Treino/Estudo/Ação geral
 * ({@link com.tatakai.manager.entity.SoloActivityType}), que são fixos e valem
 * para qualquer TimeSkip.
 */
@Service
public class TimeSkipActivityService {

    private final TimeSkipActivityRepository activityRepository;
    private final TimeSkipRepository timeSkipRepository;
    private final CampaignMemberRepository memberRepository;

    public TimeSkipActivityService(TimeSkipActivityRepository activityRepository,
                                   TimeSkipRepository timeSkipRepository,
                                   CampaignMemberRepository memberRepository) {
        this.activityRepository = activityRepository;
        this.timeSkipRepository = timeSkipRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public TimeSkipActivityResponse create(UUID campaignId, UUID timeSkipId, UUID requesterId,
                                           TimeSkipActivityRequest req) {
        TimeSkip timeSkip = loadTimeSkipOfCampaign(timeSkipId, campaignId);
        requireCampaignMaster(campaignId, requesterId);
        requireActive(timeSkip);

        TimeSkipActivity activity = TimeSkipActivity.builder()
                .timeSkip(timeSkip)
                .name(req.name())
                .description(req.description())
                .idlePointCost(req.idlePointCost())
                .build();

        try {
            return toResponse(activityRepository.save(activity));
        } catch (DataIntegrityViolationException e) {
            throw new InvalidActivityNameException();
        }
    }

    @Transactional
    public TimeSkipActivityResponse update(UUID campaignId, UUID timeSkipId, UUID activityId,
                                           UUID requesterId, TimeSkipActivityRequest req) {
        TimeSkip timeSkip = loadTimeSkipOfCampaign(timeSkipId, campaignId);
        requireCampaignMaster(campaignId, requesterId);
        requireActive(timeSkip);

        TimeSkipActivity activity = activityRepository.findByIdAndTimeSkipId(activityId, timeSkipId)
                .orElseThrow(TimeSkipActivityNotFoundException::new);

        activity.setName(req.name());
        activity.setDescription(req.description());
        activity.setIdlePointCost(req.idlePointCost());

        try {
            return toResponse(activityRepository.save(activity));
        } catch (DataIntegrityViolationException e) {
            throw new InvalidActivityNameException();
        }
    }

    @Transactional
    public void delete(UUID campaignId, UUID timeSkipId, UUID activityId, UUID requesterId) {
        TimeSkip timeSkip = loadTimeSkipOfCampaign(timeSkipId, campaignId);
        requireCampaignMaster(campaignId, requesterId);
        requireActive(timeSkip);

        TimeSkipActivity activity = activityRepository.findByIdAndTimeSkipId(activityId, timeSkipId)
                .orElseThrow(TimeSkipActivityNotFoundException::new);
        activityRepository.delete(activity);
    }

    @Transactional(readOnly = true)
    public List<TimeSkipActivityResponse> listForTimeSkip(UUID campaignId, UUID timeSkipId, UUID requesterId) {
        loadTimeSkipOfCampaign(timeSkipId, campaignId);
        if (!memberRepository.existsByCampaignIdAndUserId(campaignId, requesterId)) {
            throw new AccessDeniedException("Você não pertence a esta campanha");
        }
        return activityRepository.findByTimeSkipIdOrderByCreatedAt(timeSkipId).stream()
                .map(this::toResponse)
                .toList();
    }

    private void requireActive(TimeSkip timeSkip) {
        if (timeSkip.getStatus() != TimeSkipStatus.ACTIVE) {
            throw new TimeSkipClosedException();
        }
    }

    private TimeSkip loadTimeSkipOfCampaign(UUID timeSkipId, UUID campaignId) {
        TimeSkip timeSkip = timeSkipRepository.findById(timeSkipId)
                .orElseThrow(TimeSkipNotFoundException::new);
        if (!timeSkip.getCampaign().getId().equals(campaignId)) {
            throw new TimeSkipNotFoundException();
        }
        return timeSkip;
    }

    private void requireCampaignMaster(UUID campaignId, UUID requesterId) {
        if (!memberRepository.existsByCampaignIdAndUserIdAndRole(campaignId, requesterId, Role.MASTER)) {
            throw new AccessDeniedException("Apenas o Mestre da campanha pode realizar esta ação");
        }
    }

    private TimeSkipActivityResponse toResponse(TimeSkipActivity a) {
        return new TimeSkipActivityResponse(
                a.getId(),
                a.getTimeSkip().getId(),
                a.getName(),
                a.getDescription(),
                a.getIdlePointCost(),
                a.getCreatedAt());
    }
}
