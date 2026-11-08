package com.tatakai.manager.timeskip;

import com.tatakai.manager.auth.entity.User;
import com.tatakai.manager.auth.repository.UserRepository;
import com.tatakai.manager.campaign.dto.response.CampaignUpdateMessage;
import com.tatakai.manager.campaign.entity.Campaign;
import com.tatakai.manager.campaign.entity.CampaignMember;
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
import com.tatakai.manager.timeskip.service.TimeSkipSubmissionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TimeSkipSubmissionService — envio do TimeSkip para aprovação")
class TimeSkipSubmissionServiceTest {

    @Mock private TimeSkipSubmissionRepository submissionRepository;
    @Mock private TimeSkipRepository timeSkipRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher events;

    private TimeSkipSubmissionService service;

    private User master;
    private User player;
    private User other;
    private Campaign campaign;
    private TimeSkip ts;

    @BeforeEach
    void setUp() {
        service = new TimeSkipSubmissionService(submissionRepository, timeSkipRepository, memberRepository,
                userRepository, events);
        master = User.builder().id(UUID.randomUUID()).name("Mestre").build();
        player = User.builder().id(UUID.randomUUID()).name("Ana").build();
        other = User.builder().id(UUID.randomUUID()).name("Bia").build();
        campaign = Campaign.builder().id(UUID.randomUUID()).name("Aether").master(master).build();
        ts = TimeSkip.builder().id(UUID.randomUUID()).campaign(campaign).name("Inverno")
                .totalDays((short) 5).mode(TimeSkipMode.TIMESKIP).status(TimeSkipStatus.ACTIVE).build();
        lenient().when(timeSkipRepository.findById(ts.getId())).thenReturn(Optional.of(ts));
    }

    private void member(User u, Role role) {
        when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), u.getId()))
                .thenReturn(Optional.of(CampaignMember.builder().campaign(campaign).user(u).role(role).build()));
    }

    private TimeSkipSubmission existing(User u, SubmissionStatus status) {
        TimeSkipSubmission s = TimeSkipSubmission.builder().id(UUID.randomUUID()).timeSkipId(ts.getId())
                .userId(u.getId()).status(status).submittedAt(java.time.Instant.now()).build();
        when(submissionRepository.findByTimeSkipIdAndUserId(ts.getId(), u.getId())).thenReturn(Optional.of(s));
        return s;
    }

    private void saveEchoes() {
        when(submissionRepository.save(any(TimeSkipSubmission.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void assertTimeSkipEventPublished() {
        verify(events).publishEvent(new CampaignUpdateMessage(campaign.getId(), "TIMESKIP", ts.getId()));
    }

    // ---------- enviar ----------

    @Test
    @DisplayName("enviar: sem linha cria PENDING e avisa a campanha")
    void submit_creates_pending() {
        member(player, Role.PLAYER);
        when(submissionRepository.findByTimeSkipIdAndUserId(ts.getId(), player.getId())).thenReturn(Optional.empty());
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        saveEchoes();

        TimeSkipSubmissionResponse res = service.submit(ts.getId(), player.getId());

        assertThat(res.status()).isEqualTo("PENDING");
        assertThat(res.userId()).isEqualTo(player.getId());
        assertThat(res.userName()).isEqualTo("Ana");
        assertThat(res.submittedAt()).isNotNull();
        assertThat(res.reviewedAt()).isNull();
        assertTimeSkipEventPublished();
    }

    @Test
    @DisplayName("enviar: após recusa (REJECTED) volta a PENDING e limpa nota/revisão")
    void submit_afterRejected_resetsToPending() {
        member(player, Role.PLAYER);
        TimeSkipSubmission s = existing(player, SubmissionStatus.REJECTED);
        s.setNote("falta detalhe");
        s.setReviewedAt(java.time.Instant.now());
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        saveEchoes();

        TimeSkipSubmissionResponse res = service.submit(ts.getId(), player.getId());

        assertThat(res.status()).isEqualTo("PENDING");
        assertThat(res.note()).isNull();
        assertThat(res.reviewedAt()).isNull();
    }

    @Test
    @DisplayName("enviar: já PENDING ou APPROVED é travado (409)")
    void submit_whenPendingOrApproved_locked() {
        member(player, Role.PLAYER);
        existing(player, SubmissionStatus.PENDING);
        assertThatThrownBy(() -> service.submit(ts.getId(), player.getId()))
                .isInstanceOf(SubmissionLockedException.class);

        TimeSkipSubmission s = submissionRepository.findByTimeSkipIdAndUserId(ts.getId(), player.getId()).get();
        s.setStatus(SubmissionStatus.APPROVED);
        assertThatThrownBy(() -> service.submit(ts.getId(), player.getId()))
                .isInstanceOf(SubmissionLockedException.class);
        verify(submissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("enviar: TimeSkip CLOSED -> TimeSkipClosedException")
    void submit_closedTimeSkip_throws() {
        ts.setStatus(TimeSkipStatus.CLOSED);
        member(player, Role.PLAYER);

        assertThatThrownBy(() -> service.submit(ts.getId(), player.getId()))
                .isInstanceOf(TimeSkipClosedException.class);
    }

    @Test
    @DisplayName("enviar: IDLE_PERIOD não tem envio (400)")
    void submit_idlePeriod_throws() {
        ts.setMode(TimeSkipMode.IDLE_PERIOD);
        member(player, Role.PLAYER);

        assertThatThrownBy(() -> service.submit(ts.getId(), player.getId()))
                .isInstanceOf(InvalidTimeSkipException.class);
    }

    @Test
    @DisplayName("enviar: Mestre e não-membro recebem 403")
    void submit_masterOrOutsider_accessDenied() {
        member(master, Role.MASTER);
        assertThatThrownBy(() -> service.submit(ts.getId(), master.getId()))
                .isInstanceOf(AccessDeniedException.class);

        when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), other.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(ts.getId(), other.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("enviar: TimeSkip inexistente -> 404")
    void submit_unknownTimeSkip_notFound() {
        assertThatThrownBy(() -> service.submit(UUID.randomUUID(), player.getId()))
                .isInstanceOf(TimeSkipNotFoundException.class);
    }

    // ---------- reabrir ----------

    @Test
    @DisplayName("reabrir: PENDING e REJECTED apagam a linha e avisam a campanha")
    void reopen_pendingOrRejected_deletes() {
        member(player, Role.PLAYER);
        TimeSkipSubmission s = existing(player, SubmissionStatus.PENDING);

        service.reopen(ts.getId(), player.getId());

        verify(submissionRepository).delete(s);
        assertTimeSkipEventPublished();

        s.setStatus(SubmissionStatus.REJECTED);
        service.reopen(ts.getId(), player.getId());
        verify(submissionRepository, times(2)).delete(s);
    }

    @Test
    @DisplayName("reabrir: APPROVED é travado (409)")
    void reopen_approved_locked() {
        member(player, Role.PLAYER);
        existing(player, SubmissionStatus.APPROVED);

        assertThatThrownBy(() -> service.reopen(ts.getId(), player.getId()))
                .isInstanceOf(SubmissionLockedException.class);
        verify(submissionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("reabrir: sem linha é no-op (idempotente)")
    void reopen_noSubmission_noop() {
        member(player, Role.PLAYER);
        when(submissionRepository.findByTimeSkipIdAndUserId(ts.getId(), player.getId())).thenReturn(Optional.empty());

        service.reopen(ts.getId(), player.getId());

        verify(submissionRepository, never()).delete(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    // ---------- revisar ----------

    @Test
    @DisplayName("revisar: Mestre aprova PENDING (APPROVED + reviewedAt) e avisa a campanha")
    void review_approve() {
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(campaign.getId(), master.getId(), Role.MASTER))
                .thenReturn(true);
        existing(player, SubmissionStatus.PENDING);
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        saveEchoes();

        TimeSkipSubmissionResponse res = service.review(ts.getId(), player.getId(), master.getId(),
                new ReviewSubmissionRequest(true, null));

        assertThat(res.status()).isEqualTo("APPROVED");
        assertThat(res.reviewedAt()).isNotNull();
        assertTimeSkipEventPublished();
    }

    @Test
    @DisplayName("revisar: Mestre recusa com nota (REJECTED)")
    void review_reject_withNote() {
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(campaign.getId(), master.getId(), Role.MASTER))
                .thenReturn(true);
        existing(player, SubmissionStatus.PENDING);
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        saveEchoes();

        TimeSkipSubmissionResponse res = service.review(ts.getId(), player.getId(), master.getId(),
                new ReviewSubmissionRequest(false, "Treino demais"));

        assertThat(res.status()).isEqualTo("REJECTED");
        assertThat(res.note()).isEqualTo("Treino demais");
    }

    @Test
    @DisplayName("revisar: só o Mestre (403)")
    void review_nonMaster_accessDenied() {
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(campaign.getId(), player.getId(), Role.MASTER))
                .thenReturn(false);

        assertThatThrownBy(() -> service.review(ts.getId(), player.getId(), player.getId(),
                new ReviewSubmissionRequest(true, null)))
                .isInstanceOf(AccessDeniedException.class);
        verify(submissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("revisar: fora de PENDING -> SubmissionLocked (409)")
    void review_notPending_locked() {
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(campaign.getId(), master.getId(), Role.MASTER))
                .thenReturn(true);
        existing(player, SubmissionStatus.APPROVED);

        assertThatThrownBy(() -> service.review(ts.getId(), player.getId(), master.getId(),
                new ReviewSubmissionRequest(false, null)))
                .isInstanceOf(SubmissionLockedException.class);
        verify(submissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("revisar: jogador sem envio -> 404")
    void review_noSubmission_notFound() {
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(campaign.getId(), master.getId(), Role.MASTER))
                .thenReturn(true);
        when(submissionRepository.findByTimeSkipIdAndUserId(ts.getId(), player.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.review(ts.getId(), player.getId(), master.getId(),
                new ReviewSubmissionRequest(true, null)))
                .isInstanceOf(TimeSkipNotFoundException.class);
    }

    // ---------- listar ----------

    @Test
    @DisplayName("listar: Mestre vê todos os envios")
    void list_master_seesAll() {
        member(master, Role.MASTER);
        TimeSkipSubmission a = TimeSkipSubmission.builder().timeSkipId(ts.getId()).userId(player.getId())
                .status(SubmissionStatus.PENDING).build();
        TimeSkipSubmission b = TimeSkipSubmission.builder().timeSkipId(ts.getId()).userId(other.getId())
                .status(SubmissionStatus.APPROVED).build();
        when(submissionRepository.findByTimeSkipId(ts.getId())).thenReturn(List.of(a, b));
        when(userRepository.findAllById(any())).thenReturn(List.of(player, other));

        List<TimeSkipSubmissionResponse> res = service.list(ts.getId(), master.getId());

        assertThat(res).extracting(TimeSkipSubmissionResponse::userName).containsExactly("Ana", "Bia");
        assertThat(res).extracting(TimeSkipSubmissionResponse::status).containsExactly("PENDING", "APPROVED");
    }

    @Test
    @DisplayName("listar: jogador vê só o próprio envio")
    void list_player_seesOnlyOwn() {
        member(player, Role.PLAYER);
        existing(player, SubmissionStatus.REJECTED);
        when(userRepository.findAllById(any())).thenReturn(List.of(player));

        List<TimeSkipSubmissionResponse> res = service.list(ts.getId(), player.getId());

        assertThat(res).hasSize(1);
        assertThat(res.get(0).userId()).isEqualTo(player.getId());
        verify(submissionRepository, never()).findByTimeSkipId(any());
    }

    @Test
    @DisplayName("listar: não-membro recebe 403")
    void list_outsider_accessDenied() {
        when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), other.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(ts.getId(), other.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
