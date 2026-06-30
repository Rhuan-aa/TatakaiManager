package com.tatakai.manager.timeskip;

import com.tatakai.manager.dto.request.CreateTimeSkipRequest;
import com.tatakai.manager.dto.response.TimeSkipResponse;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.ActiveTimeSkipExistsException;
import com.tatakai.manager.exception.InvalidTimeSkipException;
import com.tatakai.manager.exception.TimeSkipClosedException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CampaignRepository;
import com.tatakai.manager.repository.TimeSkipRepository;
import com.tatakai.manager.service.TimeSkipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TimeSkipService — Sprint 4 (US-09, US-10, US-11)")
class TimeSkipServiceTest {

    @Mock private TimeSkipRepository timeSkipRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignMemberRepository memberRepository;

    private TimeSkipService service;

    private User master;
    private User player;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        service = new TimeSkipService(timeSkipRepository, campaignRepository, memberRepository);
        master = User.builder().id(UUID.randomUUID()).name("Mestre").build();
        player = User.builder().id(UUID.randomUUID()).name("Player").build();
        campaign = Campaign.builder().id(UUID.randomUUID()).name("Aether").master(master).build();
    }

    private void mockMaster(boolean isMaster) {
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(
                campaign.getId(), master.getId(), Role.MASTER)).thenReturn(isMaster);
    }

    @Test
    @DisplayName("US-09: Mestre cria TimeSkip e os dias são gerados (status ACTIVE)")
    void create_byMaster_generatesDays() {
        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        mockMaster(true);
        when(timeSkipRepository.existsByCampaignIdAndStatus(campaign.getId(), TimeSkipStatus.ACTIVE))
                .thenReturn(false);
        when(timeSkipRepository.save(any(TimeSkip.class))).thenAnswer(inv -> {
            TimeSkip ts = inv.getArgument(0);
            ts.setId(UUID.randomUUID());
            return ts;
        });

        TimeSkipResponse res = service.create(campaign.getId(), master.getId(),
                new CreateTimeSkipRequest("Inverno em Velmoor", (short) 5));

        assertThat(res.name()).isEqualTo("Inverno em Velmoor");
        assertThat(res.totalDays()).isEqualTo((short) 5);
        assertThat(res.status()).isEqualTo(TimeSkipStatus.ACTIVE);

        ArgumentCaptor<TimeSkip> captor = ArgumentCaptor.forClass(TimeSkip.class);
        verify(timeSkipRepository).save(captor.capture());
        assertThat(captor.getValue().getDays()).hasSize(5);
        assertThat(captor.getValue().getDays()).extracting(TimeSkipDay::getDayNumber)
                .containsExactly((short) 1, (short) 2, (short) 3, (short) 4, (short) 5);
    }

    @Test
    @DisplayName("US-09: criar com TimeSkip já ativo é rejeitado (409)")
    void create_withActiveExisting_throwsConflict() {
        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        mockMaster(true);
        when(timeSkipRepository.existsByCampaignIdAndStatus(campaign.getId(), TimeSkipStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(campaign.getId(), master.getId(),
                new CreateTimeSkipRequest("Outro", (short) 3)))
                .isInstanceOf(ActiveTimeSkipExistsException.class);

        verify(timeSkipRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-09: jogador não pode criar TimeSkip — AccessDenied")
    void create_byNonMaster_throwsAccessDenied() {
        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(
                campaign.getId(), player.getId(), Role.MASTER)).thenReturn(false);

        assertThatThrownBy(() -> service.create(campaign.getId(), player.getId(),
                new CreateTimeSkipRequest("X", (short) 3)))
                .isInstanceOf(AccessDeniedException.class);

        verify(timeSkipRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-10: Mestre encerra TimeSkip ativo (status CLOSED + closedAt)")
    void close_byMaster_setsClosed() {
        TimeSkip ts = TimeSkip.builder()
                .id(UUID.randomUUID()).campaign(campaign).name("Inverno")
                .totalDays((short) 5).status(TimeSkipStatus.ACTIVE).build();
        when(timeSkipRepository.findById(ts.getId())).thenReturn(Optional.of(ts));
        mockMaster(true);
        when(timeSkipRepository.save(any(TimeSkip.class))).thenAnswer(inv -> inv.getArgument(0));

        TimeSkipResponse res = service.close(ts.getId(), master.getId());

        assertThat(res.status()).isEqualTo(TimeSkipStatus.CLOSED);
        assertThat(res.closedAt()).isNotNull();
        assertThat(ts.getStatus()).isEqualTo(TimeSkipStatus.CLOSED);
    }

    @Test
    @DisplayName("US-10: encerrar TimeSkip já encerrado é rejeitado")
    void close_alreadyClosed_throws() {
        TimeSkip ts = TimeSkip.builder()
                .id(UUID.randomUUID()).campaign(campaign).name("Inverno")
                .totalDays((short) 5).status(TimeSkipStatus.CLOSED).build();
        when(timeSkipRepository.findById(ts.getId())).thenReturn(Optional.of(ts));
        mockMaster(true);

        assertThatThrownBy(() -> service.close(ts.getId(), master.getId()))
                .isInstanceOf(TimeSkipClosedException.class);

        verify(timeSkipRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-10: jogador não pode encerrar TimeSkip — AccessDenied")
    void close_byNonMaster_throwsAccessDenied() {
        TimeSkip ts = TimeSkip.builder()
                .id(UUID.randomUUID()).campaign(campaign).name("Inverno")
                .totalDays((short) 5).status(TimeSkipStatus.ACTIVE).build();
        when(timeSkipRepository.findById(ts.getId())).thenReturn(Optional.of(ts));
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(
                campaign.getId(), player.getId(), Role.MASTER)).thenReturn(false);

        assertThatThrownBy(() -> service.close(ts.getId(), player.getId()))
                .isInstanceOf(AccessDeniedException.class);

        verify(timeSkipRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-11: membro consulta histórico de TimeSkips da campanha")
    void listForCampaign_returnsHistory() {
        TimeSkip ativo = TimeSkip.builder().id(UUID.randomUUID()).campaign(campaign)
                .name("Atual").totalDays((short) 5).status(TimeSkipStatus.ACTIVE).build();
        TimeSkip antigo = TimeSkip.builder().id(UUID.randomUUID()).campaign(campaign)
                .name("Passado").totalDays((short) 3).status(TimeSkipStatus.CLOSED).build();

        when(memberRepository.existsByCampaignIdAndUserId(campaign.getId(), player.getId()))
                .thenReturn(true);
        when(timeSkipRepository.findByCampaignIdOrderByCreatedAtDesc(campaign.getId()))
                .thenReturn(List.of(ativo, antigo));

        List<TimeSkipResponse> result = service.listForCampaign(campaign.getId(), player.getId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TimeSkipResponse::name)
                .containsExactly("Atual", "Passado");
    }

    @Test
    @DisplayName("US-11: não-membro não consulta TimeSkips da campanha")
    void listForCampaign_byNonMember_throwsAccessDenied() {
        when(memberRepository.existsByCampaignIdAndUserId(campaign.getId(), player.getId()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.listForCampaign(campaign.getId(), player.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Tempo de jogo: Mestre avança manualmente o dia atual da campanha")
    void setCurrentDay_byMaster_updates() {
        TimeSkip ts = TimeSkip.builder().id(UUID.randomUUID()).campaign(campaign)
                .name("Inverno").totalDays((short) 7).currentDay((short) 1)
                .status(TimeSkipStatus.ACTIVE).build();
        when(timeSkipRepository.findById(ts.getId())).thenReturn(Optional.of(ts));
        mockMaster(true);
        when(timeSkipRepository.save(any(TimeSkip.class))).thenAnswer(inv -> inv.getArgument(0));

        TimeSkipResponse res = service.setCurrentDay(ts.getId(), master.getId(), (short) 4);

        assertThat(res.currentDay()).isEqualTo((short) 4);
        assertThat(ts.getCurrentDay()).isEqualTo((short) 4);
    }

    @Test
    @DisplayName("Tempo de jogo: jogador não pode avançar o dia — AccessDenied")
    void setCurrentDay_byNonMaster_throwsAccessDenied() {
        TimeSkip ts = TimeSkip.builder().id(UUID.randomUUID()).campaign(campaign)
                .name("Inverno").totalDays((short) 7).status(TimeSkipStatus.ACTIVE).build();
        when(timeSkipRepository.findById(ts.getId())).thenReturn(Optional.of(ts));
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(
                campaign.getId(), player.getId(), Role.MASTER)).thenReturn(false);

        assertThatThrownBy(() -> service.setCurrentDay(ts.getId(), player.getId(), (short) 3))
                .isInstanceOf(AccessDeniedException.class);

        verify(timeSkipRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tempo de jogo: dia fora do intervalo (1..totalDays) é rejeitado (400)")
    void setCurrentDay_outOfRange_throws() {
        TimeSkip ts = TimeSkip.builder().id(UUID.randomUUID()).campaign(campaign)
                .name("Inverno").totalDays((short) 7).status(TimeSkipStatus.ACTIVE).build();
        when(timeSkipRepository.findById(ts.getId())).thenReturn(Optional.of(ts));
        mockMaster(true);

        assertThatThrownBy(() -> service.setCurrentDay(ts.getId(), master.getId(), (short) 8))
                .isInstanceOf(InvalidTimeSkipException.class);

        verify(timeSkipRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tempo de jogo: não se avança o dia de um TimeSkip encerrado")
    void setCurrentDay_onClosed_throws() {
        TimeSkip ts = TimeSkip.builder().id(UUID.randomUUID()).campaign(campaign)
                .name("Velho").totalDays((short) 7).status(TimeSkipStatus.CLOSED).build();
        when(timeSkipRepository.findById(ts.getId())).thenReturn(Optional.of(ts));
        mockMaster(true);

        assertThatThrownBy(() -> service.setCurrentDay(ts.getId(), master.getId(), (short) 3))
                .isInstanceOf(TimeSkipClosedException.class);

        verify(timeSkipRepository, never()).save(any());
    }
}
