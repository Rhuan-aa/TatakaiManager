package com.tatakai.manager.log;

import com.tatakai.manager.dto.request.CreateLogRequest;
import com.tatakai.manager.dto.response.LogResponse;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.BookingNotFoundException;
import com.tatakai.manager.repository.BookingRepository;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.InteractionLogRepository;
import com.tatakai.manager.security.TextSanitizer;
import com.tatakai.manager.service.LogService;
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
@DisplayName("LogService — Sprint 7 (US-16, US-17 + log do Mestre)")
class LogServiceTest {

    @Mock private InteractionLogRepository logRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private CampaignMemberRepository memberRepository;

    // Sanitizador real para validar a remoção de XSS de verdade
    private final TextSanitizer sanitizer = new TextSanitizer();

    private LogService service;

    private User master;
    private User player;
    private User otherPlayer;
    private Campaign campaign;
    private Booking playerBooking;

    @BeforeEach
    void setUp() {
        service = new LogService(logRepository, bookingRepository, memberRepository, sanitizer);
        master = User.builder().id(UUID.randomUUID()).name("Mestre").build();
        player = User.builder().id(UUID.randomUUID()).name("Ana").build();
        otherPlayer = User.builder().id(UUID.randomUUID()).name("Bruno").build();
        campaign = Campaign.builder().id(UUID.randomUUID()).name("Aether").master(master).build();

        TimeSkip ts = TimeSkip.builder().id(UUID.randomUUID()).campaign(campaign)
                .name("Inverno").totalDays((short) 7).build();
        TimeSkipDay day = TimeSkipDay.builder().id(UUID.randomUUID()).timeSkip(ts).dayNumber((short) 3).build();
        Npc npc = Npc.builder().id(UUID.randomUUID()).name("Aldric").owner(master).build();
        playerBooking = Booking.builder().id(UUID.randomUUID())
                .timeSkipDay(day).npc(npc).user(player)
                .slotNumber((short) 1).interactionName("Treino").trainPointCost((short) 2).build();
    }

    private void mockMember(User user, Role role) {
        when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), user.getId()))
                .thenReturn(Optional.of(CampaignMember.builder()
                        .campaign(campaign).user(user).role(role).build()));
    }

    @Test
    @DisplayName("US-16: jogador registra log no próprio agendamento (texto sanitizado)")
    void createLog_byBookingOwner_persistsAndSanitizes() {
        mockMember(player, Role.PLAYER);
        when(bookingRepository.findById(playerBooking.getId())).thenReturn(Optional.of(playerBooking));
        when(logRepository.save(any(InteractionLog.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new CreateLogRequest(playerBooking.getId(),
                "Treinei <script>alert('x')</script> esgrima com Aldric");

        LogResponse res = service.create(campaign.getId(), player.getId(), req);

        assertThat(res.authorName()).isEqualTo("Ana");
        assertThat(res.npcName()).isEqualTo("Aldric");
        assertThat(res.narrative()).doesNotContain("<script>");

        ArgumentCaptor<InteractionLog> captor = ArgumentCaptor.forClass(InteractionLog.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getBooking()).isEqualTo(playerBooking);
        assertThat(captor.getValue().getNarrative()).doesNotContain("<");
    }

    @Test
    @DisplayName("Mestre adiciona log no agendamento de um jogador")
    void createLog_byMasterOnPlayerBooking_allowed() {
        mockMember(master, Role.MASTER);
        when(bookingRepository.findById(playerBooking.getId())).thenReturn(Optional.of(playerBooking));
        when(logRepository.save(any(InteractionLog.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new CreateLogRequest(playerBooking.getId(), "Aldric notou o talento de Ana");

        LogResponse res = service.create(campaign.getId(), master.getId(), req);

        assertThat(res.authorName()).isEqualTo("Mestre");
        assertThat(res.bookingId()).isEqualTo(playerBooking.getId());
    }

    @Test
    @DisplayName("Mestre adiciona log livre da campanha (sem agendamento)")
    void createLog_freeLog_byMaster_allowed() {
        mockMember(master, Role.MASTER);
        when(logRepository.save(any(InteractionLog.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new CreateLogRequest(null, "Uma nevasca caiu sobre Velmoor naquela noite");

        LogResponse res = service.create(campaign.getId(), master.getId(), req);

        assertThat(res.bookingId()).isNull();
        assertThat(res.npcName()).isNull();
        assertThat(res.narrative()).contains("nevasca");
        verify(bookingRepository, never()).findById(any());
    }

    @Test
    @DisplayName("jogador NÃO pode adicionar log livre (sem agendamento) — AccessDenied")
    void createLog_freeLog_byPlayer_throwsAccessDenied() {
        mockMember(player, Role.PLAYER);

        var req = new CreateLogRequest(null, "tentando log livre");

        assertThatThrownBy(() -> service.create(campaign.getId(), player.getId(), req))
                .isInstanceOf(AccessDeniedException.class);

        verify(logRepository, never()).save(any());
    }

    @Test
    @DisplayName("jogador não pode registrar log no agendamento de outro — AccessDenied")
    void createLog_onSomeoneElsesBooking_throwsAccessDenied() {
        mockMember(otherPlayer, Role.PLAYER);
        when(bookingRepository.findById(playerBooking.getId())).thenReturn(Optional.of(playerBooking));

        var req = new CreateLogRequest(playerBooking.getId(), "log indevido");

        assertThatThrownBy(() -> service.create(campaign.getId(), otherPlayer.getId(), req))
                .isInstanceOf(AccessDeniedException.class);

        verify(logRepository, never()).save(any());
    }

    @Test
    @DisplayName("não-membro não pode registrar log — AccessDenied")
    void createLog_byNonMember_throwsAccessDenied() {
        when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), player.getId()))
                .thenReturn(Optional.empty());

        var req = new CreateLogRequest(playerBooking.getId(), "x");

        assertThatThrownBy(() -> service.create(campaign.getId(), player.getId(), req))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("log com agendamento de outra campanha é rejeitado (404)")
    void createLog_bookingOfAnotherCampaign_throwsNotFound() {
        mockMember(master, Role.MASTER);
        Campaign other = Campaign.builder().id(UUID.randomUUID()).name("Outra").master(master).build();
        TimeSkip ts2 = TimeSkip.builder().id(UUID.randomUUID()).campaign(other).totalDays((short) 3).build();
        TimeSkipDay d2 = TimeSkipDay.builder().id(UUID.randomUUID()).timeSkip(ts2).dayNumber((short) 1).build();
        Booking alien = Booking.builder().id(UUID.randomUUID()).timeSkipDay(d2)
                .npc(Npc.builder().id(UUID.randomUUID()).name("X").owner(master).build())
                .user(player).slotNumber((short) 1).interactionName("Treino").trainPointCost((short) 2).build();
        when(bookingRepository.findById(alien.getId())).thenReturn(Optional.of(alien));

        var req = new CreateLogRequest(alien.getId(), "log cruzado");

        assertThatThrownBy(() -> service.create(campaign.getId(), master.getId(), req))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    @DisplayName("US-17: membro consulta o histórico de logs da campanha")
    void listForCampaign_returnsHistory() {
        InteractionLog log = InteractionLog.builder().id(UUID.randomUUID())
                .campaign(campaign).author(player).booking(playerBooking)
                .narrative("Treino produtivo").build();
        when(memberRepository.existsByCampaignIdAndUserId(campaign.getId(), player.getId()))
                .thenReturn(true);
        when(logRepository.findByCampaignIdOrderByCreatedAtDesc(campaign.getId()))
                .thenReturn(List.of(log));

        List<LogResponse> result = service.listForCampaign(campaign.getId(), player.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).narrative()).isEqualTo("Treino produtivo");
        assertThat(result.get(0).npcName()).isEqualTo("Aldric");
    }

    @Test
    @DisplayName("US-17: não-membro não consulta os logs — AccessDenied")
    void listForCampaign_byNonMember_throwsAccessDenied() {
        when(memberRepository.existsByCampaignIdAndUserId(campaign.getId(), player.getId()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.listForCampaign(campaign.getId(), player.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
