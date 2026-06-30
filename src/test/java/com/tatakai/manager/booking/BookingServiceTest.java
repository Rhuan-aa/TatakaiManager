package com.tatakai.manager.booking;

import com.tatakai.manager.dto.request.CreateBookingRequest;
import com.tatakai.manager.dto.response.BookingResponse;
import com.tatakai.manager.dto.response.SlotUpdateMessage;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.*;
import com.tatakai.manager.repository.*;
import com.tatakai.manager.service.BookingService;
import com.tatakai.manager.websocket.SlotEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService — Sprint 5 (US-12, US-13, US-14)")
class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private TimeSkipRepository timeSkipRepository;
    @Mock private TimeSkipDayRepository timeSkipDayRepository;
    @Mock private CampaignNpcRepository campaignNpcRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private SlotEventPublisher slotEventPublisher;

    private BookingService service;

    private User master;
    private User player;
    private Campaign campaign;
    private TimeSkip activeSkip;
    private TimeSkipDay day3;
    private Npc aldric;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookingRepository, timeSkipRepository, timeSkipDayRepository,
                campaignNpcRepository, memberRepository, userRepository, slotEventPublisher);

        master = User.builder().id(UUID.randomUUID()).name("Mestre").build();
        player = User.builder().id(UUID.randomUUID()).name("Ana").build();
        campaign = Campaign.builder().id(UUID.randomUUID()).name("Aether").master(master).build();
        activeSkip = TimeSkip.builder().id(UUID.randomUUID()).campaign(campaign)
                .name("Inverno").totalDays((short) 7).status(TimeSkipStatus.ACTIVE).build();
        day3 = TimeSkipDay.builder().id(UUID.randomUUID()).timeSkip(activeSkip).dayNumber((short) 3).build();
        aldric = Npc.builder().id(UUID.randomUUID()).name("Aldric").owner(master)
                .interactionTypes(new java.util.HashSet<>(Set.of(InteractionType.TREINO))).build();
    }

    private void mockPlayerMember() {
        when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), player.getId()))
                .thenReturn(Optional.of(CampaignMember.builder()
                        .campaign(campaign).user(player).role(Role.PLAYER).build()));
    }

    private void mockVisibleAssociation() {
        when(campaignNpcRepository.findByCampaignIdAndNpcId(campaign.getId(), aldric.getId()))
                .thenReturn(Optional.of(CampaignNpc.builder()
                        .campaign(campaign).npc(aldric).visible(true).build()));
    }

    private CreateBookingRequest req(short slot) {
        return new CreateBookingRequest(aldric.getId(), (short) 3, slot, InteractionType.TREINO);
    }

    @Test
    @DisplayName("US-13: reserva de slot livre com sucesso")
    void book_availableSlot_success() {
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));
        mockVisibleAssociation();
        when(bookingRepository.existsByTimeSkipDayIdAndNpcIdAndSlotNumber(
                day3.getId(), aldric.getId(), (short) 2)).thenReturn(false);
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        BookingResponse res = service.book(campaign.getId(), activeSkip.getId(), player.getId(), req((short) 2));

        assertThat(res.npcName()).isEqualTo("Aldric");
        assertThat(res.userName()).isEqualTo("Ana");
        assertThat(res.slotNumber()).isEqualTo((short) 2);
        assertThat(res.dayNumber()).isEqualTo((short) 3);
        assertThat(res.interactionType()).isEqualTo(InteractionType.TREINO);

        // US-15: broadcast de BOOKED no canal da campanha
        var captor = org.mockito.ArgumentCaptor.forClass(SlotUpdateMessage.class);
        verify(slotEventPublisher).publish(captor.capture());
        SlotUpdateMessage msg = captor.getValue();
        assertThat(msg.event()).isEqualTo(SlotUpdateMessage.SlotEvent.BOOKED);
        assertThat(msg.campaignId()).isEqualTo(campaign.getId());
        assertThat(msg.npcId()).isEqualTo(aldric.getId());
        assertThat(msg.slotNumber()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("US-13: reservar slot já ocupado retorna conflito (409)")
    void book_takenSlot_throwsConflict() {
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));
        mockVisibleAssociation();
        when(bookingRepository.existsByTimeSkipDayIdAndNpcIdAndSlotNumber(
                day3.getId(), aldric.getId(), (short) 1)).thenReturn(true);

        assertThatThrownBy(() -> service.book(campaign.getId(), activeSkip.getId(), player.getId(), req((short) 1)))
                .isInstanceOf(SlotTakenException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-13: corrida concorrente — violação de unicidade vira conflito (409)")
    void book_concurrentRace_dataIntegrity_throwsConflict() {
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));
        mockVisibleAssociation();
        when(bookingRepository.existsByTimeSkipDayIdAndNpcIdAndSlotNumber(
                day3.getId(), aldric.getId(), (short) 1)).thenReturn(false);
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        // Dois jogadores passaram pelo existsBy; o banco rejeita o segundo via constraint única
        when(bookingRepository.save(any(Booking.class)))
                .thenThrow(new DataIntegrityViolationException("uk_booking_slot"));

        assertThatThrownBy(() -> service.book(campaign.getId(), activeSkip.getId(), player.getId(), req((short) 1)))
                .isInstanceOf(SlotTakenException.class);
    }

    @Test
    @DisplayName("US-13: mesmo jogador pode ocupar mais de um slot do mesmo NPC no dia")
    void book_multipleSlotsSameNpcSameDay_allowed() {
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));
        mockVisibleAssociation();
        // slot 3 está livre, mesmo que o jogador já tenha o slot 1
        when(bookingRepository.existsByTimeSkipDayIdAndNpcIdAndSlotNumber(
                day3.getId(), aldric.getId(), (short) 3)).thenReturn(false);
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse res = service.book(campaign.getId(), activeSkip.getId(), player.getId(), req((short) 3));

        assertThat(res.slotNumber()).isEqualTo((short) 3);
    }

    @Test
    @DisplayName("US-13: não é possível agendar em TimeSkip encerrado (409)")
    void book_onClosedTimeSkip_rejected() {
        TimeSkip closed = TimeSkip.builder().id(UUID.randomUUID()).campaign(campaign)
                .name("Velho").totalDays((short) 3).status(TimeSkipStatus.CLOSED).build();
        mockPlayerMember();
        when(timeSkipRepository.findById(closed.getId())).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.book(campaign.getId(), closed.getId(), player.getId(), req((short) 1)))
                .isInstanceOf(TimeSkipClosedException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-13: tipo de interação não aceito pelo NPC é rejeitado (400)")
    void book_invalidInteractionType_rejected() {
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));
        mockVisibleAssociation();

        var badReq = new CreateBookingRequest(aldric.getId(), (short) 3, (short) 1, InteractionType.TRABALHO);

        assertThatThrownBy(() -> service.book(campaign.getId(), activeSkip.getId(), player.getId(), badReq))
                .isInstanceOf(InvalidBookingException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-13: jogador não pode agendar NPC oculto (404)")
    void book_hiddenNpc_asPlayer_throwsNotFound() {
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));
        when(campaignNpcRepository.findByCampaignIdAndNpcId(campaign.getId(), aldric.getId()))
                .thenReturn(Optional.of(CampaignNpc.builder()
                        .campaign(campaign).npc(aldric).visible(false).build()));

        assertThatThrownBy(() -> service.book(campaign.getId(), activeSkip.getId(), player.getId(), req((short) 1)))
                .isInstanceOf(NpcNotFoundException.class);
    }

    @Test
    @DisplayName("US-13: não-membro não pode agendar (403)")
    void book_byNonMember_throwsAccessDenied() {
        when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), player.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.book(campaign.getId(), activeSkip.getId(), player.getId(), req((short) 1)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("US-14: dono cancela o próprio agendamento e libera o slot")
    void cancel_byOwner_deletesBooking() {
        Booking booking = Booking.builder().id(UUID.randomUUID())
                .timeSkipDay(day3).npc(aldric).user(player)
                .slotNumber((short) 1).interactionType(InteractionType.TREINO).build();
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        service.cancel(booking.getId(), player.getId());

        verify(bookingRepository).delete(booking);

        // US-15: broadcast de CANCELLED liberando o slot
        var captor = org.mockito.ArgumentCaptor.forClass(SlotUpdateMessage.class);
        verify(slotEventPublisher).publish(captor.capture());
        SlotUpdateMessage msg = captor.getValue();
        assertThat(msg.event()).isEqualTo(SlotUpdateMessage.SlotEvent.CANCELLED);
        assertThat(msg.npcId()).isEqualTo(aldric.getId());
        assertThat(msg.slotNumber()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("Tempo de jogo: jogador não pode cancelar agendamento de dia que já passou (409)")
    void cancel_pastBooking_rejected() {
        // dia atual da campanha = 5; o agendamento é no dia 3 (já passou)
        activeSkip.setCurrentDay((short) 5);
        Booking booking = Booking.builder().id(UUID.randomUUID())
                .timeSkipDay(day3).npc(aldric).user(player)
                .slotNumber((short) 1).interactionType(InteractionType.TREINO).build();
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancel(booking.getId(), player.getId()))
                .isInstanceOf(BookingAlreadyPassedException.class);

        verify(bookingRepository, never()).delete(any());
        verify(slotEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("Tempo de jogo: não é possível agendar em um dia que já passou (400)")
    void book_onPastDay_rejected() {
        activeSkip.setCurrentDay((short) 5);
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));

        assertThatThrownBy(() -> service.book(campaign.getId(), activeSkip.getId(), player.getId(), req((short) 1)))
                .isInstanceOf(InvalidBookingException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-14: jogador não pode cancelar agendamento de outro (403)")
    void cancel_byNonOwner_throwsAccessDenied() {
        Booking booking = Booking.builder().id(UUID.randomUUID())
                .timeSkipDay(day3).npc(aldric).user(player)
                .slotNumber((short) 1).interactionType(InteractionType.TREINO).build();
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancel(booking.getId(), master.getId()))
                .isInstanceOf(AccessDeniedException.class);

        verify(bookingRepository, never()).delete(any());
    }

    @Test
    @DisplayName("US-12: lista agendamentos do TimeSkip para um membro")
    void listBookings_returnsForTimeSkip() {
        Booking b = Booking.builder().id(UUID.randomUUID())
                .timeSkipDay(day3).npc(aldric).user(player)
                .slotNumber((short) 1).interactionType(InteractionType.TREINO).build();
        when(memberRepository.existsByCampaignIdAndUserId(campaign.getId(), player.getId()))
                .thenReturn(true);
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(bookingRepository.findByTimeSkipDay_TimeSkipId(activeSkip.getId()))
                .thenReturn(java.util.List.of(b));

        var result = service.listBookings(campaign.getId(), activeSkip.getId(), player.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).slotNumber()).isEqualTo((short) 1);
    }
}
