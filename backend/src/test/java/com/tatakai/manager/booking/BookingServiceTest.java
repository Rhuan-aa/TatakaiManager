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
    @Mock private NpcRepository npcRepository;
    @Mock private TimeSkipActivityRepository timeSkipActivityRepository;
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
                campaignNpcRepository, memberRepository, userRepository, npcRepository,
                timeSkipActivityRepository, slotEventPublisher);

        master = User.builder().id(UUID.randomUUID()).name("Mestre").build();
        player = User.builder().id(UUID.randomUUID()).name("Ana").build();
        campaign = Campaign.builder().id(UUID.randomUUID()).name("Aether").master(master).build();
        activeSkip = TimeSkip.builder().id(UUID.randomUUID()).campaign(campaign)
                .name("Inverno").totalDays((short) 7).status(TimeSkipStatus.ACTIVE).build();
        day3 = TimeSkipDay.builder().id(UUID.randomUUID()).timeSkip(activeSkip).dayNumber((short) 3).build();
        aldric = Npc.builder().id(UUID.randomUUID()).name("Aldric").ownerId(master.getId())
                .interactions(new java.util.ArrayList<>(java.util.List.of(
                        new NpcInteraction("Treino", "Treino", "Sessão de treino", (short) 2)))).build();
    }

    private void mockPlayerMember() {
        when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), player.getId()))
                .thenReturn(Optional.of(CampaignMember.builder()
                        .campaign(campaign).user(player).role(Role.PLAYER).build()));
    }

    private void mockVisibleAssociation() {
        when(campaignNpcRepository.findByCampaignIdAndNpcId(campaign.getId(), aldric.getId()))
                .thenReturn(Optional.of(CampaignNpc.builder()
                        .campaign(campaign).npcId(aldric.getId()).visible(true).build()));
        when(npcRepository.findById(aldric.getId())).thenReturn(Optional.of(aldric));
    }

    private CreateBookingRequest req(short slot) {
        return new CreateBookingRequest(aldric.getId(), (short) 3, slot, "Treino", null, null, null);
    }

    private CreateBookingRequest soloReq(short slot, SoloActivityType type, String description) {
        return new CreateBookingRequest(null, (short) 3, slot, null, type, null, description);
    }

    private CreateBookingRequest customActivityReq(short slot, UUID activityId) {
        return new CreateBookingRequest(null, (short) 3, slot, null, null, activityId, null);
    }

    @Test
    @DisplayName("US-13: reserva de slot livre com sucesso")
    void book_availableSlot_success() {
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));
        mockVisibleAssociation();
        when(bookingRepository.findByTimeSkipDayIdAndNpcId(day3.getId(), aldric.getId()))
                .thenReturn(java.util.List.of());
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
        assertThat(res.interactionName()).isEqualTo("Treino");
        assertThat(res.idlePointCost()).isEqualTo((short) 2);

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
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        Booking existing = Booking.builder().id(UUID.randomUUID()).timeSkipDay(day3)
                .npcId(aldric.getId()).user(master).slotNumber((short) 1)
                .interactionName("Treino").idlePointCost((short) 1).build();
        when(bookingRepository.findByTimeSkipDayIdAndNpcId(day3.getId(), aldric.getId()))
                .thenReturn(java.util.List.of(existing));

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
        when(bookingRepository.findByTimeSkipDayIdAndNpcId(day3.getId(), aldric.getId()))
                .thenReturn(java.util.List.of());
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        // Dois jogadores passaram pela checagem de sobreposição; o banco rejeita o segundo via constraint única
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
        // a faixa 3-4 está livre: a reserva existente no slot 1 (custo 2) ocupa só 1-2
        Booking existing = Booking.builder().id(UUID.randomUUID()).timeSkipDay(day3)
                .npcId(aldric.getId()).user(player).slotNumber((short) 1)
                .interactionName("Treino").idlePointCost((short) 2).build();
        when(bookingRepository.findByTimeSkipDayIdAndNpcId(day3.getId(), aldric.getId()))
                .thenReturn(java.util.List.of(existing));
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

        var badReq = new CreateBookingRequest(aldric.getId(), (short) 3, (short) 1, "Trabalho", null, null, null);

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
                        .campaign(campaign).npcId(aldric.getId()).visible(false).build()));

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

    // ---------- treino solo (sem NPC) ----------

    @Test
    @DisplayName("Treino solo: reserva de slot sem NPC com sucesso")
    void book_soloActivity_success() {
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));
        when(bookingRepository.findByTimeSkipDayIdAndUserIdAndNpcIdIsNull(
                day3.getId(), player.getId())).thenReturn(java.util.List.of());
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        BookingResponse res = service.book(campaign.getId(), activeSkip.getId(), player.getId(),
                soloReq((short) 2, SoloActivityType.TREINO, "Treinar esgrima sozinho no pátio"));

        assertThat(res.npcId()).isNull();
        assertThat(res.npcName()).isNull();
        assertThat(res.interactionName()).isNull();
        assertThat(res.soloActivityType()).isEqualTo(SoloActivityType.TREINO);
        assertThat(res.description()).isEqualTo("Treinar esgrima sozinho no pátio");
        assertThat(res.idlePointCost()).isEqualTo((short) 1);

        var captor = org.mockito.ArgumentCaptor.forClass(SlotUpdateMessage.class);
        verify(slotEventPublisher).publish(captor.capture());
        assertThat(captor.getValue().npcId()).isNull();
        assertThat(captor.getValue().soloActivityType()).isEqualTo(SoloActivityType.TREINO);
    }

    @Test
    @DisplayName("Treino solo: mesmo jogador não pode ocupar duas vezes o mesmo slot do dia (409)")
    void book_soloActivity_slotTakenBySameUser_rejected() {
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        Booking existing = Booking.builder().id(UUID.randomUUID()).timeSkipDay(day3)
                .user(player).slotNumber((short) 1)
                .soloActivityType(SoloActivityType.TREINO).description("Treino matinal")
                .idlePointCost((short) 1).build();
        when(bookingRepository.findByTimeSkipDayIdAndUserIdAndNpcIdIsNull(
                day3.getId(), player.getId())).thenReturn(java.util.List.of(existing));

        assertThatThrownBy(() -> service.book(campaign.getId(), activeSkip.getId(), player.getId(),
                soloReq((short) 1, SoloActivityType.ESTUDO, "Estudar táticas de combate")))
                .isInstanceOf(SlotTakenException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Treino solo: descrição em branco é rejeitada (400)")
    void book_soloActivity_blankDescription_rejected() {
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));

        assertThatThrownBy(() -> service.book(campaign.getId(), activeSkip.getId(), player.getId(),
                soloReq((short) 1, SoloActivityType.ACAO_GERAL, "  ")))
                .isInstanceOf(InvalidBookingException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Treino solo: tipo da atividade é obrigatório (400)")
    void book_soloActivity_missingType_rejected() {
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));

        assertThatThrownBy(() -> service.book(campaign.getId(), activeSkip.getId(), player.getId(),
                soloReq((short) 1, null, "Alguma descrição")))
                .isInstanceOf(InvalidBookingException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Atividade customizada do TimeSkip: reserva com sucesso, usa custo do catálogo")
    void book_customTimeSkipActivity_success() {
        TimeSkipActivity rebuild = TimeSkipActivity.builder().id(UUID.randomUUID())
                .timeSkip(activeSkip).name("Reconstrução da vila")
                .description("Ajudar a reconstruir as casas destruídas").idlePointCost((short) 3).build();
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));
        when(bookingRepository.findByTimeSkipDayIdAndUserIdAndNpcIdIsNull(
                day3.getId(), player.getId())).thenReturn(java.util.List.of());
        when(timeSkipActivityRepository.findByIdAndTimeSkipId(rebuild.getId(), activeSkip.getId()))
                .thenReturn(Optional.of(rebuild));
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse res = service.book(campaign.getId(), activeSkip.getId(), player.getId(),
                customActivityReq((short) 2, rebuild.getId()));

        assertThat(res.soloActivityType()).isNull();
        assertThat(res.activityId()).isEqualTo(rebuild.getId());
        assertThat(res.activityName()).isEqualTo("Reconstrução da vila");
        assertThat(res.description()).isEqualTo("Ajudar a reconstruir as casas destruídas");
        assertThat(res.idlePointCost()).isEqualTo((short) 3);
    }

    @Test
    @DisplayName("Atividade customizada de outro TimeSkip é rejeitada (404)")
    void book_customTimeSkipActivity_fromOtherTimeSkip_rejected() {
        UUID foreignActivityId = UUID.randomUUID();
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));
        when(timeSkipActivityRepository.findByIdAndTimeSkipId(foreignActivityId, activeSkip.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.book(campaign.getId(), activeSkip.getId(), player.getId(),
                customActivityReq((short) 2, foreignActivityId)))
                .isInstanceOf(TimeSkipActivityNotFoundException.class);

        verify(bookingRepository, never()).save(any());
    }

    // ---------- ocupação por custo: cada ponto de ócio ocupa um slot ----------

    @Test
    @DisplayName("Ocupação: ação de custo 2 bloqueia também o slot seguinte do NPC (409)")
    void book_rangeOverlap_throwsConflict() {
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));
        mockVisibleAssociation();
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        // reserva existente no slot 2 com custo 2 ocupa a faixa 2-3
        Booking existing = Booking.builder().id(UUID.randomUUID()).timeSkipDay(day3)
                .npcId(aldric.getId()).user(master).slotNumber((short) 2)
                .interactionName("Treino").idlePointCost((short) 2).build();
        when(bookingRepository.findByTimeSkipDayIdAndNpcId(day3.getId(), aldric.getId()))
                .thenReturn(java.util.List.of(existing));

        // agendar no slot 3 colide com o fim da faixa do existente
        assertThatThrownBy(() -> service.book(campaign.getId(), activeSkip.getId(), player.getId(), req((short) 3)))
                .isInstanceOf(SlotTakenException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ocupação: ação que não cabe nos slots restantes do dia é rejeitada (400)")
    void book_costDoesNotFitDay_rejected() {
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));
        mockVisibleAssociation();
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));

        // Treino custa 2: no slot 4 a faixa iria até o slot 5, além dos 4 do dia
        assertThatThrownBy(() -> service.book(campaign.getId(), activeSkip.getId(), player.getId(), req((short) 4)))
                .isInstanceOf(InvalidBookingException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Slot extra: ação de custo zero só pode usar o slot extra (400)")
    void book_zeroCost_inNormalSlot_rejected() {
        aldric.getInteractions().add(new NpcInteraction("Conversa", "Conversa", "Só um papo", (short) 0));
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));
        mockVisibleAssociation();
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));

        var zeroReq = new CreateBookingRequest(aldric.getId(), (short) 3, (short) 1, "Conversa", null, null, null);

        assertThatThrownBy(() -> service.book(campaign.getId(), activeSkip.getId(), player.getId(), zeroReq))
                .isInstanceOf(InvalidBookingException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Slot extra: ação com custo maior que zero não pode usar o slot extra (400)")
    void book_nonZeroCost_inExtraSlot_rejected() {
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));
        mockVisibleAssociation();
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));

        // Treino custa 2 — não entra no slot extra (5)
        assertThatThrownBy(() -> service.book(campaign.getId(), activeSkip.getId(), player.getId(),
                req(TimeSkipDay.EXTRA_SLOT_NUMBER)))
                .isInstanceOf(InvalidBookingException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Slot extra: ação de custo zero agendada no slot extra com sucesso")
    void book_zeroCost_inExtraSlot_success() {
        TimeSkipActivity festival = TimeSkipActivity.builder().id(UUID.randomUUID())
                .timeSkip(activeSkip).name("Festival da colheita")
                .description("Participar do festival da vila").idlePointCost((short) 0).build();
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));
        when(timeSkipActivityRepository.findByIdAndTimeSkipId(festival.getId(), activeSkip.getId()))
                .thenReturn(Optional.of(festival));
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(bookingRepository.existsByTimeSkipDayIdAndUserIdAndSlotNumber(
                day3.getId(), player.getId(), TimeSkipDay.EXTRA_SLOT_NUMBER)).thenReturn(false);
        when(bookingRepository.findByTimeSkipDayIdAndUserIdAndNpcIdIsNull(
                day3.getId(), player.getId())).thenReturn(java.util.List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse res = service.book(campaign.getId(), activeSkip.getId(), player.getId(),
                customActivityReq(TimeSkipDay.EXTRA_SLOT_NUMBER, festival.getId()));

        assertThat(res.slotNumber()).isEqualTo(TimeSkipDay.EXTRA_SLOT_NUMBER);
        assertThat(res.idlePointCost()).isEqualTo((short) 0);
    }

    @Test
    @DisplayName("Slot extra: jogador só pode usar o slot extra uma vez por dia (400)")
    void book_secondExtraSlotSameDay_rejected() {
        TimeSkipActivity festival = TimeSkipActivity.builder().id(UUID.randomUUID())
                .timeSkip(activeSkip).name("Festival da colheita")
                .description("Participar do festival da vila").idlePointCost((short) 0).build();
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));
        when(timeSkipActivityRepository.findByIdAndTimeSkipId(festival.getId(), activeSkip.getId()))
                .thenReturn(Optional.of(festival));
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(bookingRepository.existsByTimeSkipDayIdAndUserIdAndSlotNumber(
                day3.getId(), player.getId(), TimeSkipDay.EXTRA_SLOT_NUMBER)).thenReturn(true);

        assertThatThrownBy(() -> service.book(campaign.getId(), activeSkip.getId(), player.getId(),
                customActivityReq(TimeSkipDay.EXTRA_SLOT_NUMBER, festival.getId())))
                .isInstanceOf(InvalidBookingException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Treino solo: faixas de custo do próprio jogador não podem se sobrepor (409)")
    void book_soloRangeOverlap_rejected() {
        TimeSkipActivity rebuild = TimeSkipActivity.builder().id(UUID.randomUUID())
                .timeSkip(activeSkip).name("Reconstrução da vila")
                .description("Ajudar a reconstruir as casas destruídas").idlePointCost((short) 3).build();
        mockPlayerMember();
        when(timeSkipRepository.findById(activeSkip.getId())).thenReturn(Optional.of(activeSkip));
        when(timeSkipDayRepository.findByTimeSkipIdAndDayNumber(activeSkip.getId(), (short) 3))
                .thenReturn(Optional.of(day3));
        when(timeSkipActivityRepository.findByIdAndTimeSkipId(rebuild.getId(), activeSkip.getId()))
                .thenReturn(Optional.of(rebuild));
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        // treino existente no slot 3 (custo 1); a atividade de custo 3 no slot 1 ocuparia 1-3
        Booking existing = Booking.builder().id(UUID.randomUUID()).timeSkipDay(day3)
                .user(player).slotNumber((short) 3)
                .soloActivityType(SoloActivityType.TREINO).description("Treino noturno")
                .idlePointCost((short) 1).build();
        when(bookingRepository.findByTimeSkipDayIdAndUserIdAndNpcIdIsNull(
                day3.getId(), player.getId())).thenReturn(java.util.List.of(existing));

        assertThatThrownBy(() -> service.book(campaign.getId(), activeSkip.getId(), player.getId(),
                customActivityReq((short) 1, rebuild.getId())))
                .isInstanceOf(SlotTakenException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Treino solo: dono cancela e libera o slot")
    void cancel_soloActivity_deletesBooking() {
        Booking booking = Booking.builder().id(UUID.randomUUID())
                .timeSkipDay(day3).user(player).slotNumber((short) 1)
                .soloActivityType(SoloActivityType.ESTUDO).description("Estudar história local")
                .idlePointCost((short) 0).build();
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        service.cancel(booking.getId(), player.getId());

        verify(bookingRepository).delete(booking);

        var captor = org.mockito.ArgumentCaptor.forClass(SlotUpdateMessage.class);
        verify(slotEventPublisher).publish(captor.capture());
        assertThat(captor.getValue().npcId()).isNull();
        assertThat(captor.getValue().slotNumber()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("US-14: dono cancela o próprio agendamento e libera o slot")
    void cancel_byOwner_deletesBooking() {
        Booking booking = Booking.builder().id(UUID.randomUUID())
                .timeSkipDay(day3).npcId(aldric.getId()).npcName(aldric.getName()).user(player)
                .slotNumber((short) 1).interactionName("Treino").idlePointCost((short) 2).build();
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
                .timeSkipDay(day3).npcId(aldric.getId()).npcName(aldric.getName()).user(player)
                .slotNumber((short) 1).interactionName("Treino").idlePointCost((short) 2).build();
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
                .timeSkipDay(day3).npcId(aldric.getId()).npcName(aldric.getName()).user(player)
                .slotNumber((short) 1).interactionName("Treino").idlePointCost((short) 2).build();
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancel(booking.getId(), master.getId()))
                .isInstanceOf(AccessDeniedException.class);

        verify(bookingRepository, never()).delete(any());
    }

    @Test
    @DisplayName("US-12: lista agendamentos do TimeSkip para um membro")
    void listBookings_returnsForTimeSkip() {
        Booking b = Booking.builder().id(UUID.randomUUID())
                .timeSkipDay(day3).npcId(aldric.getId()).npcName(aldric.getName()).user(player)
                .slotNumber((short) 1).interactionName("Treino").idlePointCost((short) 2).build();
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
