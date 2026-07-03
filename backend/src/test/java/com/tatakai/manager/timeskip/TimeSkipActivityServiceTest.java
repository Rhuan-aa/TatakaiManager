package com.tatakai.manager.timeskip;

import com.tatakai.manager.dto.request.TimeSkipActivityRequest;
import com.tatakai.manager.dto.response.TimeSkipActivityResponse;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.InvalidActivityNameException;
import com.tatakai.manager.exception.TimeSkipActivityNotFoundException;
import com.tatakai.manager.exception.TimeSkipClosedException;
import com.tatakai.manager.exception.TimeSkipNotFoundException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.TimeSkipActivityRepository;
import com.tatakai.manager.repository.TimeSkipRepository;
import com.tatakai.manager.service.TimeSkipActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TimeSkipActivityService — catálogo de atividades solo exclusivas de um TimeSkip")
class TimeSkipActivityServiceTest {

    @Mock private TimeSkipActivityRepository activityRepository;
    @Mock private TimeSkipRepository timeSkipRepository;
    @Mock private CampaignMemberRepository memberRepository;

    private TimeSkipActivityService service;

    private User master;
    private User player;
    private Campaign campaign;
    private TimeSkip activeSkip;

    @BeforeEach
    void setUp() {
        service = new TimeSkipActivityService(activityRepository, timeSkipRepository, memberRepository);
        master = User.builder().id(UUID.randomUUID()).name("Mestre").build();
        player = User.builder().id(UUID.randomUUID()).name("Ana").build();
        campaign = Campaign.builder().id(UUID.randomUUID()).name("Aether").master(master).build();
        activeSkip = TimeSkip.builder().id(UUID.randomUUID()).campaign(campaign)
                .name("Inverno").totalDays((short) 7).status(TimeSkipStatus.ACTIVE).build();
    }

    private TimeSkipActivityRequest req() {
        return new TimeSkipActivityRequest(
                "Reconstrução da vila", "Ajudar a reconstruir as casas destruídas", (short) 3);
    }

    @Test
    @DisplayName("Mestre cria atividade customizada com sucesso")
    void create_byMaster_success() {
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(campaign.getId(), master.getId(), Role.MASTER))
                .thenReturn(true);
        when(activityRepository.save(any(TimeSkipActivity.class))).thenAnswer(inv -> {
            TimeSkipActivity a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        TimeSkipActivityResponse res = service.create(campaign.getId(), activeSkip.getId(), master.getId(), req());

        assertThat(res.name()).isEqualTo("Reconstrução da vila");
        assertThat(res.idlePointCost()).isEqualTo((short) 3);
        assertThat(res.timeSkipId()).isEqualTo(activeSkip.getId());
    }

    @Test
    @DisplayName("Jogador não pode criar atividade customizada (403)")
    void create_byPlayer_throwsAccessDenied() {
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(campaign.getId(), player.getId(), Role.MASTER))
                .thenReturn(false);

        assertThatThrownBy(() -> service.create(campaign.getId(), activeSkip.getId(), player.getId(), req()))
                .isInstanceOf(AccessDeniedException.class);

        verify(activityRepository, never()).save(any());
    }

    @Test
    @DisplayName("Não é possível criar atividade em TimeSkip encerrado (409)")
    void create_onClosedTimeSkip_rejected() {
        TimeSkip closed = TimeSkip.builder().id(UUID.randomUUID()).campaign(campaign)
                .name("Velho").totalDays((short) 3).status(TimeSkipStatus.CLOSED).build();
        when(timeSkipRepository.findById(closed.getId())).thenReturn(Optional.of(closed));
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(campaign.getId(), master.getId(), Role.MASTER))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(campaign.getId(), closed.getId(), master.getId(), req()))
                .isInstanceOf(TimeSkipClosedException.class);

        verify(activityRepository, never()).save(any());
    }

    @Test
    @DisplayName("Nome duplicado no mesmo TimeSkip é rejeitado (409)")
    void create_duplicateName_rejected() {
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(campaign.getId(), master.getId(), Role.MASTER))
                .thenReturn(true);
        when(activityRepository.save(any(TimeSkipActivity.class)))
                .thenThrow(new DataIntegrityViolationException("uk_time_skip_activity_name"));

        assertThatThrownBy(() -> service.create(campaign.getId(), activeSkip.getId(), master.getId(), req()))
                .isInstanceOf(InvalidActivityNameException.class);
    }

    @Test
    @DisplayName("TimeSkip de outra campanha não é encontrado (404)")
    void create_timeSkipFromOtherCampaign_notFound() {
        Campaign other = Campaign.builder().id(UUID.randomUUID()).name("Outra").master(master).build();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));

        assertThatThrownBy(() -> service.create(other.getId(), activeSkip.getId(), master.getId(), req()))
                .isInstanceOf(TimeSkipNotFoundException.class);
    }

    @Test
    @DisplayName("Lista atividades do TimeSkip para um membro")
    void listForTimeSkip_returnsActivities() {
        TimeSkipActivity a = TimeSkipActivity.builder().id(UUID.randomUUID()).timeSkip(activeSkip)
                .name("Reconstrução da vila").description("Ajudar a reconstruir").idlePointCost((short) 3).build();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(memberRepository.existsByCampaignIdAndUserId(campaign.getId(), player.getId())).thenReturn(true);
        when(activityRepository.findByTimeSkipIdOrderByCreatedAt(activeSkip.getId())).thenReturn(List.of(a));

        List<TimeSkipActivityResponse> result = service.listForTimeSkip(campaign.getId(), activeSkip.getId(), player.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Reconstrução da vila");
    }

    @Test
    @DisplayName("Não-membro não pode listar atividades (403)")
    void listForTimeSkip_byNonMember_throwsAccessDenied() {
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(memberRepository.existsByCampaignIdAndUserId(campaign.getId(), player.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.listForTimeSkip(campaign.getId(), activeSkip.getId(), player.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Mestre exclui atividade customizada")
    void delete_byMaster_success() {
        TimeSkipActivity a = TimeSkipActivity.builder().id(UUID.randomUUID()).timeSkip(activeSkip)
                .name("Reconstrução da vila").description("Ajudar a reconstruir").idlePointCost((short) 3).build();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(campaign.getId(), master.getId(), Role.MASTER))
                .thenReturn(true);
        when(activityRepository.findByIdAndTimeSkipId(a.getId(), activeSkip.getId())).thenReturn(Optional.of(a));

        service.delete(campaign.getId(), activeSkip.getId(), a.getId(), master.getId());

        verify(activityRepository).delete(a);
    }

    @Test
    @DisplayName("Excluir atividade inexistente retorna 404")
    void delete_notFound_rejected() {
        UUID missingId = UUID.randomUUID();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(campaign.getId(), master.getId(), Role.MASTER))
                .thenReturn(true);
        when(activityRepository.findByIdAndTimeSkipId(missingId, activeSkip.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(campaign.getId(), activeSkip.getId(), missingId, master.getId()))
                .isInstanceOf(TimeSkipActivityNotFoundException.class);
    }
}
