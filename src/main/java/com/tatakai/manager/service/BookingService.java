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
    private final SlotEventPublisher slotEventPublisher;

    public BookingService(BookingRepository bookingRepository,
                          TimeSkipRepository timeSkipRepository,
                          TimeSkipDayRepository timeSkipDayRepository,
                          CampaignNpcRepository campaignNpcRepository,
                          CampaignMemberRepository memberRepository,
                          UserRepository userRepository,
                          SlotEventPublisher slotEventPublisher) {
        this.bookingRepository = bookingRepository;
        this.timeSkipRepository = timeSkipRepository;
        this.timeSkipDayRepository = timeSkipDayRepository;
        this.campaignNpcRepository = campaignNpcRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
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

        CampaignNpc association = campaignNpcRepository
                .findByCampaignIdAndNpcId(campaignId, req.npcId())
                .orElseThrow(NpcNotFoundException::new);
        // Jogador não enxerga (nem agenda) NPC oculto — 404 para não revelar existência
        if (!association.isVisible() && role != Role.MASTER) {
            throw new NpcNotFoundException();
        }

        Npc npc = association.getNpc();
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

        Booking booking = Booking.builder()
                .timeSkipDay(day)
                .npc(npc)
                .user(user)
                .slotNumber(req.slotNumber())
                .interactionName(interaction.getName())
                .trainPointCost(interaction.getTrainPointCost())
                .build();

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
                campaignId, timeSkip.getId(), booking.getNpc().getId(),
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
                b.getNpc().getId(),
                b.getNpc().getName(),
                b.getUser().getId(),
                b.getUser().getName(),
                b.getTimeSkipDay().getDayNumber(),
                b.getSlotNumber(),
                b.getInteractionName(),
                b.getTrainPointCost(),
                b.getCreatedAt());
    }
}
