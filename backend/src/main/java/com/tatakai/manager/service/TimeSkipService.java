package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.CreateTimeSkipRequest;
import com.tatakai.manager.dto.response.TimeSkipResponse;
import com.tatakai.manager.entity.Booking;
import com.tatakai.manager.entity.Campaign;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.entity.TimeSkip;
import com.tatakai.manager.entity.TimeSkipStatus;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.ActiveTimeSkipExistsException;
import com.tatakai.manager.exception.CampaignNotFoundException;
import com.tatakai.manager.exception.InvalidTimeSkipException;
import com.tatakai.manager.exception.TimeSkipClosedException;
import com.tatakai.manager.exception.TimeSkipNotFoundException;
import com.tatakai.manager.repository.BookingRepository;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CampaignRepository;
import com.tatakai.manager.repository.InteractionLogRepository;
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
    private final BookingRepository bookingRepository;
    private final InteractionLogRepository logRepository;

    public TimeSkipService(TimeSkipRepository timeSkipRepository,
                           CampaignRepository campaignRepository,
                           CampaignMemberRepository memberRepository,
                           BookingRepository bookingRepository,
                           InteractionLogRepository logRepository) {
        this.timeSkipRepository = timeSkipRepository;
        this.campaignRepository = campaignRepository;
        this.memberRepository = memberRepository;
        this.bookingRepository = bookingRepository;
        this.logRepository = logRepository;
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

    /**
     * Exclui um TimeSkip por completo (ex.: criado por engano). Só o Mestre da campanha.
     * Remove em cascata os agendamentos do TimeSkip e os logs vinculados a eles; os dias
     * são removidos por orphanRemoval ao apagar o TimeSkip.
     */
    @Transactional
    public void delete(UUID timeSkipId, UUID requesterId) {
        TimeSkip timeSkip = timeSkipRepository.findById(timeSkipId)
                .orElseThrow(TimeSkipNotFoundException::new);
        requireCampaignMaster(timeSkip.getCampaign().getId(), requesterId);

        List<Booking> bookings = bookingRepository.findByTimeSkipDay_TimeSkipId(timeSkipId);
        if (!bookings.isEmpty()) {
            logRepository.deleteByBookingIn(bookings);
            bookingRepository.deleteAll(bookings);
        }
        timeSkipRepository.delete(timeSkip);
    }

    /**
     * Avança/ajusta manualmente o dia atual da campanha dentro do TimeSkip (tempo de jogo).
     * Só o Mestre, apenas em TimeSkip ativo, dentro do intervalo 1..totalDays.
     */
    @Transactional
    public TimeSkipResponse setCurrentDay(UUID timeSkipId, UUID requesterId, short day) {
        TimeSkip timeSkip = timeSkipRepository.findById(timeSkipId)
                .orElseThrow(TimeSkipNotFoundException::new);
        requireCampaignMaster(timeSkip.getCampaign().getId(), requesterId);

        if (timeSkip.getStatus() == TimeSkipStatus.CLOSED) {
            throw new TimeSkipClosedException();
        }
        if (day < 1 || day > timeSkip.getTotalDays()) {
            throw new InvalidTimeSkipException(
                    "O dia deve estar entre 1 e " + timeSkip.getTotalDays());
        }

        timeSkip.setCurrentDay(day);
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
                ts.getCurrentDay(),
                ts.getStatus(),
                ts.getCreatedAt(),
                ts.getClosedAt());
    }
}
