package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.CreateBookingRequest;
import com.tatakai.manager.dto.response.BookingResponse;
import com.tatakai.manager.dto.response.SlotUpdateMessage;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.*;
import com.tatakai.manager.repository.*;
import com.tatakai.manager.websocket.SlotEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TimeSkipRepository timeSkipRepository;
    private final TimeSkipDayRepository timeSkipDayRepository;
    private final CampaignNpcRepository campaignNpcRepository;
    private final CampaignMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final NpcRepository npcRepository;
    private final TimeSkipActivityRepository timeSkipActivityRepository;
    private final SlotEventPublisher slotEventPublisher;

    public BookingService(BookingRepository bookingRepository,
                          TimeSkipRepository timeSkipRepository,
                          TimeSkipDayRepository timeSkipDayRepository,
                          CampaignNpcRepository campaignNpcRepository,
                          CampaignMemberRepository memberRepository,
                          UserRepository userRepository,
                          NpcRepository npcRepository,
                          TimeSkipActivityRepository timeSkipActivityRepository,
                          SlotEventPublisher slotEventPublisher) {
        this.bookingRepository = bookingRepository;
        this.timeSkipRepository = timeSkipRepository;
        this.timeSkipDayRepository = timeSkipDayRepository;
        this.campaignNpcRepository = campaignNpcRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.npcRepository = npcRepository;
        this.timeSkipActivityRepository = timeSkipActivityRepository;
        this.slotEventPublisher = slotEventPublisher;
    }

    // ---------- US-13: agendar ----------

    @Transactional
    public BookingResponse book(UUID campaignId, UUID timeSkipId, UUID requesterId,
                                CreateBookingRequest req) {
        Role role = requireMembership(campaignId, requesterId);

        TimeSkip timeSkip = loadTimeSkipOfCampaign(timeSkipId, campaignId);
        if (timeSkip.getStatus() != TimeSkipStatus.ACTIVE) {
            throw new TimeSkipClosedException();
        }

        TimeSkipDay day = timeSkipDayRepository
                .findByTimeSkipIdAndDayNumber(timeSkipId, req.dayNumber())
                .orElseThrow(() -> new InvalidBookingException("Dia inválido para este TimeSkip"));

        // Tempo de jogo: não se agenda em um dia que já passou
        if (day.getDayNumber() < timeSkip.getCurrentDay()) {
            throw new InvalidBookingException("Não é possível agendar em um dia que já passou");
        }

        Booking booking = req.npcId() != null
                ? buildNpcBooking(campaignId, role, day, req, requesterId)
                : buildSoloBooking(day, req, requesterId);

        BookingResponse response;
        try {
            response = toResponse(bookingRepository.save(booking));
        } catch (DataIntegrityViolationException e) {
            // Dois jogadores passaram pela verificação ao mesmo tempo; o banco rejeitou o segundo
            throw new SlotTakenException();
        }

        // US-15: notifica as telas dos demais jogadores em tempo real
        slotEventPublisher.publish(SlotUpdateMessage.booked(campaignId, response, timeSkipId));
        return response;
    }

    private Booking buildNpcBooking(UUID campaignId, Role role, TimeSkipDay day,
                                    CreateBookingRequest req, UUID requesterId) {
        if (req.interactionName() == null || req.interactionName().isBlank()) {
            throw new InvalidBookingException("o tipo de interação é obrigatório");
        }

        CampaignNpc association = campaignNpcRepository
                .findByCampaignIdAndNpcId(campaignId, req.npcId())
                .orElseThrow(NpcNotFoundException::new);
        // Jogador não enxerga (nem agenda) NPC oculto — 404 para não revelar existência
        if (!association.isVisible() && role != Role.MASTER) {
            throw new NpcNotFoundException();
        }

        Npc npc = npcRepository.findById(association.getNpcId()).orElseThrow(NpcNotFoundException::new);
        NpcInteraction interaction = npc.getInteractions().stream()
                .filter(i -> i.getName().equals(req.interactionName()))
                .findFirst()
                .orElseThrow(() -> new InvalidBookingException(
                        "Este NPC não aceita a interação: " + req.interactionName()));

        // Verificação otimista; a constraint única é a garantia final em concorrência
        if (bookingRepository.existsByTimeSkipDayIdAndNpcIdAndSlotNumber(
                day.getId(), npc.getId(), req.slotNumber())) {
            throw new SlotTakenException();
        }

        User user = userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException("Usuário autenticado não encontrado"));

        return Booking.builder()
                .timeSkipDay(day)
                .npcId(npc.getId())
                .npcName(npc.getName())
                .user(user)
                .slotNumber(req.slotNumber())
                .interactionName(interaction.getName())
                .idlePointCost(interaction.getIdlePointCost())
                .build();
    }

    // ---------- treino solo: mesmo slot, sem NPC ----------

    private static final short FIXED_SOLO_ACTIVITY_COST = 1;

    private Booking buildSoloBooking(TimeSkipDay day, CreateBookingRequest req, UUID requesterId) {
        if (req.soloActivityType() == null && req.activityId() == null) {
            throw new InvalidBookingException("o tipo da atividade solo é obrigatório");
        }
        if (req.soloActivityType() != null && req.activityId() != null) {
            throw new InvalidBookingException(
                    "informe o tipo fixo ou a atividade customizada, não os dois");
        }

        // Verificação otimista; sem NPC não há constraint única no banco — o mesmo
        // jogador não pode ocupar duas vezes o mesmo slot do dia com atividades solo
        if (bookingRepository.existsByTimeSkipDayIdAndUserIdAndSlotNumberAndNpcIdIsNull(
                day.getId(), requesterId, req.slotNumber())) {
            throw new SlotTakenException();
        }

        User user = userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException("Usuário autenticado não encontrado"));

        Booking.BookingBuilder booking = Booking.builder()
                .timeSkipDay(day)
                .user(user)
                .slotNumber(req.slotNumber());

        if (req.activityId() != null) {
            TimeSkipActivity activity = timeSkipActivityRepository
                    .findByIdAndTimeSkipId(req.activityId(), day.getTimeSkip().getId())
                    .orElseThrow(TimeSkipActivityNotFoundException::new);
            return booking
                    .timeSkipActivityId(activity.getId())
                    .activityName(activity.getName())
                    .description(activity.getDescription())
                    .idlePointCost(activity.getIdlePointCost())
                    .build();
        }

        if (req.description() == null || req.description().isBlank()) {
            throw new InvalidBookingException("a descrição da atividade solo é obrigatória");
        }
        return booking
                .soloActivityType(req.soloActivityType())
                .description(req.description())
                .idlePointCost(FIXED_SOLO_ACTIVITY_COST)
                .build();
    }

    // ---------- US-14: cancelar ----------

    @Transactional
    public void cancel(UUID bookingId, UUID requesterId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(BookingNotFoundException::new);
        if (!booking.getUser().getId().equals(requesterId)) {
            throw new AccessDeniedException("Você só pode cancelar os seus próprios agendamentos");
        }

        // Captura os dados do slot antes de remover, para o broadcast
        TimeSkip timeSkip = booking.getTimeSkipDay().getTimeSkip();

        // Tempo de jogo: só é possível desmarcar agendamentos cujo dia ainda não passou
        if (booking.getTimeSkipDay().getDayNumber() < timeSkip.getCurrentDay()) {
            throw new BookingAlreadyPassedException();
        }

        UUID campaignId = timeSkip.getCampaign().getId();
        SlotUpdateMessage message = SlotUpdateMessage.cancelled(
                campaignId, timeSkip.getId(), booking.getId(), booking.getNpcId(),
                booking.getTimeSkipDay().getDayNumber(), booking.getSlotNumber());

        bookingRepository.delete(booking);

        // US-15: libera o slot nas telas dos jogadores em tempo real
        slotEventPublisher.publish(message);
    }

    // ---------- US-12: visualizar disponibilidade ----------

    @Transactional(readOnly = true)
    public List<BookingResponse> listBookings(UUID campaignId, UUID timeSkipId, UUID requesterId) {
        if (!memberRepository.existsByCampaignIdAndUserId(campaignId, requesterId)) {
            throw new AccessDeniedException("Você não pertence a esta campanha");
        }
        loadTimeSkipOfCampaign(timeSkipId, campaignId);
        return bookingRepository.findByTimeSkipDay_TimeSkipId(timeSkipId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ---------- helpers ----------

    private Role requireMembership(UUID campaignId, UUID requesterId) {
        return memberRepository.findByCampaignIdAndUserId(campaignId, requesterId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"))
                .getRole();
    }

    private TimeSkip loadTimeSkipOfCampaign(UUID timeSkipId, UUID campaignId) {
        TimeSkip timeSkip = timeSkipRepository.findById(timeSkipId)
                .orElseThrow(TimeSkipNotFoundException::new);
        if (!timeSkip.getCampaign().getId().equals(campaignId)) {
            throw new TimeSkipNotFoundException();
        }
        return timeSkip;
    }

    private BookingResponse toResponse(Booking b) {
        return new BookingResponse(
                b.getId(),
                b.getNpcId(),
                b.getNpcName(),
                b.getUser().getId(),
                b.getUser().getName(),
                b.getTimeSkipDay().getDayNumber(),
                b.getSlotNumber(),
                b.getInteractionName(),
                b.getIdlePointCost(),
                b.getSoloActivityType(),
                b.getTimeSkipActivityId(),
                b.getActivityName(),
                b.getDescription(),
                b.getCreatedAt());
    }
}
