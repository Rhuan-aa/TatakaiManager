package com.tatakai.manager.timeskip.service;

import com.tatakai.manager.auth.entity.User;
import com.tatakai.manager.auth.repository.UserRepository;
import com.tatakai.manager.campaign.dto.response.CampaignUpdateMessage;
import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.shared.exception.AccessDeniedException;
import com.tatakai.manager.timeskip.dto.request.ReviewSubmissionRequest;
import com.tatakai.manager.timeskip.dto.response.TimeSkipSubmissionResponse;
import com.tatakai.manager.timeskip.entity.SubmissionStatus;
import com.tatakai.manager.timeskip.entity.TimeSkip;
import com.tatakai.manager.timeskip.entity.TimeSkipMode;
import com.tatakai.manager.timeskip.entity.TimeSkipStatus;
import com.tatakai.manager.timeskip.entity.TimeSkipSubmission;
import com.tatakai.manager.timeskip.exception.InvalidTimeSkipException;
import com.tatakai.manager.timeskip.exception.SubmissionLockedException;
import com.tatakai.manager.timeskip.exception.TimeSkipClosedException;
import com.tatakai.manager.timeskip.exception.TimeSkipNotFoundException;
import com.tatakai.manager.timeskip.repository.TimeSkipRepository;
import com.tatakai.manager.timeskip.repository.TimeSkipSubmissionRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Envio dos agendamentos do TimeSkip (modo TIMESKIP) para aprovação do Mestre, com reabertura para edição. */
@Service
public class TimeSkipSubmissionService {

    private final TimeSkipSubmissionRepository submissionRepository;
    private final TimeSkipRepository timeSkipRepository;
    private final CampaignMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    public TimeSkipSubmissionService(TimeSkipSubmissionRepository submissionRepository,
                                     TimeSkipRepository timeSkipRepository,
                                     CampaignMemberRepository memberRepository,
                                     UserRepository userRepository,
                                     ApplicationEventPublisher events) {
        this.submissionRepository = submissionRepository;
        this.timeSkipRepository = timeSkipRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.events = events;
    }

    /** Jogador envia (ou reenvia após recusa) os próprios agendamentos. */
    @Transactional
    public TimeSkipSubmissionResponse submit(UUID timeSkipId, UUID requesterId) {
        TimeSkip ts = load(timeSkipId);
        requirePlayer(ts, requesterId);
        requireActiveTimeSkipMode(ts);

        TimeSkipSubmission sub = submissionRepository.findByTimeSkipIdAndUserId(timeSkipId, requesterId)
                .orElse(null);
        if (sub != null && sub.getStatus() != SubmissionStatus.REJECTED) {
            throw new SubmissionLockedException("Agendamentos já enviados: reabra para editar");
        }
        if (sub == null) {
            sub = TimeSkipSubmission.builder().timeSkipId(timeSkipId).userId(requesterId).build();
        }
        sub.setStatus(SubmissionStatus.PENDING);
        sub.setNote(null);
        sub.setSubmittedAt(Instant.now());
        sub.setReviewedAt(null);
        sub = submissionRepository.save(sub);

        changed(ts);
        return toResponse(sub, userName(requesterId));
    }

    /** "Editar de novo": apaga o envio do próprio jogador (PENDING ou REJECTED; APPROVED é travado). */
    @Transactional
    public void reopen(UUID timeSkipId, UUID requesterId) {
        TimeSkip ts = load(timeSkipId);
        requirePlayer(ts, requesterId);
        if (ts.getStatus() != TimeSkipStatus.ACTIVE) {
            throw new TimeSkipClosedException();
        }

        submissionRepository.findByTimeSkipIdAndUserId(timeSkipId, requesterId).ifPresent(sub -> {
            if (sub.getStatus() == SubmissionStatus.APPROVED) {
                throw new SubmissionLockedException("Agendamentos aprovados não podem ser reabertos");
            }
            submissionRepository.delete(sub);
            changed(ts);
        });
    }

    /** Mestre vê todos os envios; jogador, só o seu. */
    @Transactional(readOnly = true)
    public List<TimeSkipSubmissionResponse> list(UUID timeSkipId, UUID requesterId) {
        TimeSkip ts = load(timeSkipId);
        Role role = requireMembership(ts.getCampaign().getId(), requesterId);

        List<TimeSkipSubmission> subs = role == Role.MASTER
                ? submissionRepository.findByTimeSkipId(timeSkipId)
                : submissionRepository.findByTimeSkipIdAndUserId(timeSkipId, requesterId).stream().toList();
        Map<UUID, String> names = userRepository.findAllById(
                        subs.stream().map(TimeSkipSubmission::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
        return subs.stream().map(s -> toResponse(s, names.get(s.getUserId()))).toList();
    }

    /** Mestre aprova ou recusa um envio PENDING. */
    @Transactional
    public TimeSkipSubmissionResponse review(UUID timeSkipId, UUID userId, UUID requesterId,
                                             ReviewSubmissionRequest req) {
        TimeSkip ts = load(timeSkipId);
        if (!memberRepository.existsByCampaignIdAndUserIdAndRole(
                ts.getCampaign().getId(), requesterId, Role.MASTER)) {
            throw new AccessDeniedException("Apenas o Mestre da campanha pode realizar esta ação");
        }

        TimeSkipSubmission sub = submissionRepository.findByTimeSkipIdAndUserId(timeSkipId, userId)
                .orElseThrow(TimeSkipNotFoundException::new);
        if (sub.getStatus() != SubmissionStatus.PENDING) {
            throw new SubmissionLockedException("Este envio não está aguardando aprovação");
        }
        sub.setStatus(req.approved() ? SubmissionStatus.APPROVED : SubmissionStatus.REJECTED);
        sub.setNote(req.note());
        sub.setReviewedAt(Instant.now());
        sub = submissionRepository.save(sub);

        changed(ts);
        return toResponse(sub, userName(userId));
    }

    // ---------- helpers ----------

    private TimeSkip load(UUID timeSkipId) {
        return timeSkipRepository.findById(timeSkipId).orElseThrow(TimeSkipNotFoundException::new);
    }

    private Role requireMembership(UUID campaignId, UUID requesterId) {
        return memberRepository.findByCampaignIdAndUserId(campaignId, requesterId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"))
                .getRole();
    }

    private void requirePlayer(TimeSkip ts, UUID requesterId) {
        if (requireMembership(ts.getCampaign().getId(), requesterId) != Role.PLAYER) {
            throw new AccessDeniedException("Apenas jogadores enviam agendamentos");
        }
    }

    private void requireActiveTimeSkipMode(TimeSkip ts) {
        if (ts.getStatus() != TimeSkipStatus.ACTIVE) {
            throw new TimeSkipClosedException();
        }
        if (ts.getMode() == TimeSkipMode.IDLE_PERIOD) {
            throw new InvalidTimeSkipException("O envio para aprovação só existe no modo TimeSkip");
        }
    }

    private String userName(UUID userId) {
        return userRepository.findById(userId).map(User::getName).orElse(null);
    }

    private void changed(TimeSkip ts) {
        events.publishEvent(new CampaignUpdateMessage(ts.getCampaign().getId(), "TIMESKIP", ts.getId()));
    }

    private static TimeSkipSubmissionResponse toResponse(TimeSkipSubmission s, String userName) {
        return new TimeSkipSubmissionResponse(s.getUserId(), userName, s.getStatus().name(), s.getNote(),
                s.getSubmittedAt(), s.getReviewedAt());
    }
}
