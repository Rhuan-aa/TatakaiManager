package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.CreateLogRequest;
import com.tatakai.manager.dto.request.UpdateLogRequest;
import com.tatakai.manager.dto.response.LogResponse;
import com.tatakai.manager.entity.Booking;
import com.tatakai.manager.entity.CampaignMember;
import com.tatakai.manager.entity.InteractionLog;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.BookingNotFoundException;
import com.tatakai.manager.exception.LogNotFoundException;
import com.tatakai.manager.repository.BookingRepository;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.InteractionLogRepository;
import com.tatakai.manager.security.TextSanitizer;
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

    public LogService(InteractionLogRepository logRepository,
                      BookingRepository bookingRepository,
                      CampaignMemberRepository memberRepository,
                      TextSanitizer textSanitizer) {
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
            // Log livre (sem agendamento) é exclusivo do Mestre
            if (role != Role.MASTER) {
                throw new AccessDeniedException("Apenas o Mestre pode adicionar um log livre");
            }
        }

        InteractionLog log = InteractionLog.builder()
                .campaign(member.getCampaign())
                .author(member.getUser())
                .booking(booking)
                .narrative(textSanitizer.sanitize(req.narrative()))
                .build();

        return toResponse(logRepository.save(log));
    }

    @Transactional(readOnly = true)
    public List<LogResponse> listForCampaign(UUID campaignId, UUID requesterId) {
        if (!memberRepository.existsByCampaignIdAndUserId(campaignId, requesterId)) {
            throw new AccessDeniedException("Você não pertence a esta campanha");
        }
        return logRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream()
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

    @Transactional
    public void delete(UUID campaignId, UUID logId, UUID requesterId) {
        requireCampaignMaster(campaignId, requesterId);
        InteractionLog log = logRepository.findByIdAndCampaignId(logId, campaignId)
                .orElseThrow(LogNotFoundException::new);

        logRepository.delete(log);
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
                b == null ? null : b.getIdlePointCost());
    }
}
