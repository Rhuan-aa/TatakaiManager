package com.tatakai.manager.log.service;

import com.tatakai.manager.campaign.dto.response.CampaignUpdateMessage;
import com.tatakai.manager.campaign.entity.CampaignMember;
import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.log.dto.request.CreateLogRequest;
import com.tatakai.manager.log.dto.request.UpdateLogRequest;
import com.tatakai.manager.log.dto.response.LogResponse;
import com.tatakai.manager.log.entity.InteractionLog;
import com.tatakai.manager.log.entity.LogStatus;
import com.tatakai.manager.log.exception.LogConflictException;
import com.tatakai.manager.log.exception.LogNotFoundException;
import com.tatakai.manager.log.repository.InteractionLogRepository;
import com.tatakai.manager.shared.exception.AccessDeniedException;
import com.tatakai.manager.shared.security.TextSanitizer;
import com.tatakai.manager.timeskip.entity.Booking;
import com.tatakai.manager.timeskip.exception.BookingNotFoundException;
import com.tatakai.manager.timeskip.repository.BookingRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class LogService {

    private final InteractionLogRepository logRepository;
    private final BookingRepository bookingRepository;
    private final CampaignMemberRepository memberRepository;
    private final TextSanitizer textSanitizer;
    private final ApplicationEventPublisher events;

    public LogService(InteractionLogRepository logRepository,
                      BookingRepository bookingRepository,
                      CampaignMemberRepository memberRepository,
                      TextSanitizer textSanitizer,
                      ApplicationEventPublisher events) {
        this.events = events;
        this.logRepository = logRepository;
        this.bookingRepository = bookingRepository;
        this.memberRepository = memberRepository;
        this.textSanitizer = textSanitizer;
    }

    @Transactional
    public LogResponse create(UUID campaignId, UUID authorId, CreateLogRequest req) {
        CampaignMember member = memberRepository.findByCampaignIdAndUserId(campaignId, authorId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"));
        Role role = member.getRole();

        LogStatus status = LogStatus.PUBLISHED;
        Booking booking = null;
        if (req.bookingId() != null) {
            booking = bookingRepository.findById(req.bookingId())
                    .orElseThrow(BookingNotFoundException::new);
            // O agendamento precisa pertencer a esta campanha
            UUID bookingCampaignId = booking.getTimeSkipDay().getTimeSkip().getCampaign().getId();
            if (!bookingCampaignId.equals(campaignId)) {
                throw new BookingNotFoundException();
            }
            // Jogador só registra no próprio agendamento; o Mestre pode em qualquer um
            boolean isOwner = booking.getUser().getId().equals(authorId);
            if (!isOwner && role != Role.MASTER) {
                throw new AccessDeniedException("Você só pode registrar logs nos seus próprios agendamentos");
            }
        } else {
            // Log livre de jogador é um pedido: fica PENDING até o Mestre revisar
            if (role != Role.MASTER) status = LogStatus.PENDING;
        }

        InteractionLog log = InteractionLog.builder()
                .campaign(member.getCampaign())
                .author(member.getUser())
                .booking(booking)
                .narrative(textSanitizer.sanitize(req.narrative()))
                .status(status)
                .build();

        InteractionLog saved = logRepository.save(log);
        if (status == LogStatus.PENDING) notifyChange(campaignId, saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<LogResponse> listForCampaign(UUID campaignId, UUID requesterId) {
        if (!memberRepository.existsByCampaignIdAndUserId(campaignId, requesterId)) {
            throw new AccessDeniedException("Você não pertence a esta campanha");
        }
        boolean master = memberRepository.existsByCampaignIdAndUserIdAndRole(campaignId, requesterId, Role.MASTER);
        return logRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream()
                .filter(l -> master || l.getStatus() == LogStatus.PUBLISHED
                        || l.getAuthor().getId().equals(requesterId))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LogResponse update(UUID campaignId, UUID logId, UUID requesterId, UpdateLogRequest req) {
        requireCampaignMaster(campaignId, requesterId);
        InteractionLog log = logRepository.findByIdAndCampaignId(logId, campaignId)
                .orElseThrow(LogNotFoundException::new);

        log.setNarrative(textSanitizer.sanitize(req.narrative()));
        log.setUpdatedAt(Instant.now());

        return toResponse(logRepository.save(log));
    }

    /** Mestre aprova (PUBLISHED) ou recusa (REJECTED) um pedido PENDING. */
    @Transactional
    public LogResponse review(UUID campaignId, UUID logId, UUID requesterId, boolean approved, String note) {
        requireCampaignMaster(campaignId, requesterId);
        InteractionLog log = logRepository.findByIdAndCampaignId(logId, campaignId)
                .orElseThrow(LogNotFoundException::new);
        if (log.getStatus() != LogStatus.PENDING) {
            throw new LogConflictException("Este log não está aguardando revisão");
        }

        log.setStatus(approved ? LogStatus.PUBLISHED : LogStatus.REJECTED);
        log.setReviewNote(note == null || note.isBlank() ? null : note.trim());
        log.setUpdatedAt(Instant.now());

        InteractionLog saved = logRepository.save(log);
        notifyChange(campaignId, saved);
        return toResponse(saved);
    }

    /** Mestre apaga qualquer log; o autor só cancela o próprio pedido (não publicado). */
    @Transactional
    public void delete(UUID campaignId, UUID logId, UUID requesterId) {
        boolean master = memberRepository.existsByCampaignIdAndUserIdAndRole(campaignId, requesterId, Role.MASTER);
        InteractionLog log = logRepository.findByIdAndCampaignId(logId, campaignId)
                .orElseThrow(() -> master ? new LogNotFoundException()
                        : new AccessDeniedException("Apenas o Mestre da campanha pode realizar esta ação"));
        boolean ownRequest = log.getAuthor().getId().equals(requesterId) && log.getStatus() != LogStatus.PUBLISHED;
        if (!master && !ownRequest) {
            throw new AccessDeniedException("Apenas o Mestre da campanha pode realizar esta ação");
        }

        logRepository.delete(log);
        if (log.getStatus() != LogStatus.PUBLISHED) notifyChange(campaignId, log);
    }

    private void notifyChange(UUID campaignId, InteractionLog log) {
        events.publishEvent(new CampaignUpdateMessage(campaignId, "LOGS", log.getId()));
    }

    private void requireCampaignMaster(UUID campaignId, UUID requesterId) {
        if (!memberRepository.existsByCampaignIdAndUserIdAndRole(campaignId, requesterId, Role.MASTER)) {
            throw new AccessDeniedException("Apenas o Mestre da campanha pode realizar esta ação");
        }
    }

    private LogResponse toResponse(InteractionLog log) {
        Booking b = log.getBooking();
        return new LogResponse(
                log.getId(),
                log.getAuthor().getId(),
                log.getAuthor().getName(),
                log.getNarrative(),
                log.getCreatedAt(),
                log.getUpdatedAt(),
                b == null ? null : b.getId(),
                b == null ? null : b.getNpcId(),
                b == null ? null : b.getNpcName(),
                b == null ? null : b.getTimeSkipDay().getDayNumber(),
                b == null ? null : b.getSlotNumber(),
                b == null ? null : b.getInteractionName(),
                b == null ? null : b.getIdlePointCost(),
                log.getStatus().name(),
                log.getReviewNote());
    }
}
