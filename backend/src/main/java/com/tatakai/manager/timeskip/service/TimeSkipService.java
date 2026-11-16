package com.tatakai.manager.timeskip.service;

import com.tatakai.manager.campaign.dto.response.CampaignUpdateMessage;
import com.tatakai.manager.campaign.entity.Campaign;
import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.exception.CampaignNotFoundException;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.campaign.repository.CampaignRepository;
import com.tatakai.manager.log.repository.InteractionLogRepository;
import com.tatakai.manager.progression.entity.TrainingVenue;
import com.tatakai.manager.progression.service.TrainingService;
import com.tatakai.manager.shared.exception.AccessDeniedException;
import com.tatakai.manager.timeskip.dto.request.CreateTimeSkipRequest;
import com.tatakai.manager.timeskip.dto.response.TimeSkipResponse;
import com.tatakai.manager.timeskip.entity.Booking;
import com.tatakai.manager.timeskip.entity.TimeSkip;
import com.tatakai.manager.timeskip.entity.SubmissionStatus;
import com.tatakai.manager.timeskip.entity.TimeSkipDay;
import com.tatakai.manager.timeskip.entity.TimeSkipStatus;
import com.tatakai.manager.timeskip.exception.ActiveTimeSkipExistsException;
import com.tatakai.manager.timeskip.exception.InvalidTimeSkipException;
import com.tatakai.manager.timeskip.exception.SubmissionPendingException;
import com.tatakai.manager.timeskip.exception.TimeSkipClosedException;
import com.tatakai.manager.timeskip.exception.TimeSkipNotFoundException;
import com.tatakai.manager.timeskip.repository.BookingRepository;
import com.tatakai.manager.timeskip.repository.TimeSkipRepository;
import com.tatakai.manager.timeskip.repository.TimeSkipSubmissionRepository;

import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
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

    private final TrainingService trainingService;
    private final com.tatakai.manager.sheet.repository.CharacterSheetRepository sheetRepository;

    private final ApplicationEventPublisher events;
    private final TimeSkipSubmissionRepository submissionRepository;

    public TimeSkipService(TimeSkipRepository timeSkipRepository,
                           CampaignRepository campaignRepository,
                           CampaignMemberRepository memberRepository,
                           BookingRepository bookingRepository,
                           InteractionLogRepository logRepository,
                           TrainingService trainingService,
                           com.tatakai.manager.sheet.repository.CharacterSheetRepository sheetRepository,
                           ApplicationEventPublisher events,
                           TimeSkipSubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
        this.events = events;
        this.sheetRepository = sheetRepository;
        this.trainingService = trainingService;
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
                .slotsPerDay(req.slotsPerDay() != null ? req.slotsPerDay() : TimeSkipDay.SLOTS_PER_DAY)
                .mode(req.mode() != null ? req.mode() : com.tatakai.manager.timeskip.entity.TimeSkipMode.TIMESKIP)
                .build();
        timeSkip.generateDays();

        return changed(timeSkipRepository.save(timeSkip));
    }

    @Transactional
    public TimeSkipResponse close(UUID timeSkipId, UUID requesterId) {
        TimeSkip timeSkip = timeSkipRepository.findById(timeSkipId)
                .orElseThrow(TimeSkipNotFoundException::new);
        requireCampaignMaster(timeSkip.getCampaign().getId(), requesterId);

        if (timeSkip.getStatus() == TimeSkipStatus.CLOSED) {
            throw new TimeSkipClosedException();
        }

        // Em TIMESKIP, envios aguardando aprovação bloqueiam o encerramento (quem nunca enviou segue normal)
        if (timeSkip.getMode() != com.tatakai.manager.timeskip.entity.TimeSkipMode.IDLE_PERIOD
                && !submissionRepository.findByTimeSkipIdInAndStatus(
                        List.of(timeSkipId), SubmissionStatus.PENDING).isEmpty()) {
            throw new SubmissionPendingException("Há agendamentos aguardando aprovação");
        }

        // Consolida tudo que ainda não foi processado (em TIMESKIP, é o único momento: §51.2)
        if (timeSkip.getProcessedDay() == null) timeSkip.setProcessedDay((short) 0);
        trainingService.processUpTo(timeSkip, (short) (timeSkip.getTotalDays() + 1));
        timeSkip.setStatus(TimeSkipStatus.CLOSED);
        timeSkip.setClosedAt(Instant.now());
        return changed(timeSkipRepository.save(timeSkip));
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
        submissionRepository.deleteAll(submissionRepository.findByTimeSkipId(timeSkipId));
        timeSkipRepository.delete(timeSkip);
        events.publishEvent(new CampaignUpdateMessage(timeSkip.getCampaign().getId(), "TIMESKIP", timeSkipId));
    }

    /** Resposta + aviso em tempo real aos outros clientes da campanha. */
    private TimeSkipResponse changed(TimeSkip ts) {
        events.publishEvent(new CampaignUpdateMessage(ts.getCampaign().getId(), "TIMESKIP", ts.getId()));
        return toResponse(ts);
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

        if (timeSkip.getMode() != com.tatakai.manager.timeskip.entity.TimeSkipMode.TIMESKIP) {
            trainingService.processUpTo(timeSkip, day);
        }
        timeSkip.setCurrentDay(day);
        return changed(timeSkipRepository.save(timeSkip));
    }

    /**
     * Projeção de Desgaste Físico por jogador com ficha concluída. Mestre vê todos; jogador, só a própria.
     * Considera o que ainda será processado (dias após processedDay); Repouso de qualidade não é estimado.
     */
    @Transactional(readOnly = true)
    public List<com.tatakai.manager.timeskip.dto.response.FatiguePreviewResponse> fatiguePreview(
            UUID timeSkipId, UUID requesterId) {
        TimeSkip ts = timeSkipRepository.findById(timeSkipId).orElseThrow(TimeSkipNotFoundException::new);
        UUID campaignId = ts.getCampaign().getId();
        boolean master = memberRepository.existsByCampaignIdAndUserIdAndRole(campaignId, requesterId, Role.MASTER);
        if (!master && !memberRepository.existsByCampaignIdAndUserId(campaignId, requesterId)) {
            throw new AccessDeniedException("Você não pertence a esta campanha");
        }
        List<Booking> all = bookingRepository.findByTimeSkipDay_TimeSkipId(timeSkipId);
        return sheetRepository.findByCampaignMemberCampaignId(campaignId).stream()
                .filter(sh -> sh.getBuildStatus() == com.tatakai.manager.build.entity.BuildStatus.COMPLETED)
                .filter(sh -> master || sh.getCampaignMember().getUser().getId().equals(requesterId))
                .map(sh -> {
                    var user = sh.getCampaignMember().getUser();
                    List<Booking> mine = all.stream().filter(b -> b.getUser().getId().equals(user.getId())).toList();
                    int unrested = com.tatakai.manager.progression.service.rules.TrainingRules
                            .unrestedDays(mine, ts.getTotalDays(), ts.getSlotsPerDay());
                    return new com.tatakai.manager.timeskip.dto.response.FatiguePreviewResponse(user.getId(),
                            user.getName(), sh.getPhysicalFatigue(), sh.getMentalFatigue(), unrested,
                            sh.getPhysicalFatigue() + unrested);
                }).toList();
    }

    /** Local de treino do TimeSkip (RN-PROG-03). Só o Mestre. */
    @Transactional
    public TimeSkipResponse setVenue(UUID timeSkipId, UUID requesterId, TrainingVenue venue) {
        TimeSkip timeSkip = timeSkipRepository.findById(timeSkipId).orElseThrow(TimeSkipNotFoundException::new);
        requireCampaignMaster(timeSkip.getCampaign().getId(), requesterId);
        timeSkip.setVenue(venue);
        return changed(timeSkipRepository.save(timeSkip));
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
                ts.getClosedAt(),
                ts.getVenue(),
                ts.getMode() != null ? ts.getMode() : com.tatakai.manager.timeskip.entity.TimeSkipMode.TIMESKIP,
                ts.getSlotsPerDay(),
                ts.extraSlotNumber());
    }
}
