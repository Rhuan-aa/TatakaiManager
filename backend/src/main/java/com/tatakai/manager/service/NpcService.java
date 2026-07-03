package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.CreateNpcRequest;
import com.tatakai.manager.dto.request.NpcAttributesDto;
import com.tatakai.manager.dto.request.NpcDetailDto;
import com.tatakai.manager.dto.request.NpcInteractionDto;
import com.tatakai.manager.dto.request.UpdateNpcRequest;
import com.tatakai.manager.dto.response.CampaignNpcResponse;
import com.tatakai.manager.dto.response.NpcResponse;
import com.tatakai.manager.dto.response.NpcSummaryResponse;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.CampaignNotFoundException;
import com.tatakai.manager.exception.InvalidImageException;
import com.tatakai.manager.exception.NpcAlreadyAssociatedException;
import com.tatakai.manager.exception.NpcNotFoundException;
import com.tatakai.manager.exception.UserNotFoundException;
import com.tatakai.manager.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NpcService {

    private final NpcRepository npcRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignNpcRepository campaignNpcRepository;
    private final CampaignMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final NpcImageRepository npcImageRepository;
    private final BookingRepository bookingRepository;
    private final InteractionLogRepository logRepository;

    public NpcService(NpcRepository npcRepository,
                      CampaignRepository campaignRepository,
                      CampaignNpcRepository campaignNpcRepository,
                      CampaignMemberRepository memberRepository,
                      UserRepository userRepository,
                      NpcImageRepository npcImageRepository,
                      BookingRepository bookingRepository,
                      InteractionLogRepository logRepository) {
        this.npcRepository = npcRepository;
        this.campaignRepository = campaignRepository;
        this.campaignNpcRepository = campaignNpcRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.npcImageRepository = npcImageRepository;
        this.bookingRepository = bookingRepository;
        this.logRepository = logRepository;
    }

    // ---------- US-06: criar ----------

    @Transactional
    public NpcResponse create(UUID ownerId, CreateNpcRequest req) {
        userRepository.findById(ownerId)
                .orElseThrow(() -> new UserNotFoundException("Usuário autenticado não encontrado"));

        Npc npc = Npc.builder()
                .id(UUID.randomUUID())
                .name(req.name())
                .description(req.description())
                .attributes(toAttributes(req.attributes()))
                .ownerId(ownerId)
                .knowledge(toDetails(req.knowledge()))
                .traits(toDetails(req.traits()))
                .specs(toDetails(req.specs()))
                .interactions(toInteractions(req.interactions()))
                .createdAt(Instant.now())
                .build();

        return toResponse(npcRepository.save(npc), null);
    }

    // ---------- US-07: editar ----------

    @Transactional
    public NpcResponse update(UUID npcId, UUID requesterId, UpdateNpcRequest req) {
        Npc npc = npcRepository.findById(npcId).orElseThrow(NpcNotFoundException::new);
        requireOwner(npc, requesterId);

        npc.setName(req.name());
        npc.setDescription(req.description());
        npc.setAttributes(toAttributes(req.attributes()));
        npc.getKnowledge().clear();
        npc.getKnowledge().addAll(toDetails(req.knowledge()));
        npc.getTraits().clear();
        npc.getTraits().addAll(toDetails(req.traits()));
        npc.getSpecs().clear();
        npc.getSpecs().addAll(toDetails(req.specs()));
        npc.getInteractions().clear();
        npc.getInteractions().addAll(toInteractions(req.interactions()));

        return toResponse(npcRepository.save(npc), null);
    }

    // ---------- US-08: associar a campanha ----------

    @Transactional
    public CampaignNpcResponse associate(UUID npcId, UUID campaignId, UUID requesterId) {
        Npc npc = npcRepository.findById(npcId).orElseThrow(NpcNotFoundException::new);
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(CampaignNotFoundException::new);

        requireOwner(npc, requesterId);
        requireCampaignMaster(campaignId, requesterId);

        if (campaignNpcRepository.existsByCampaignIdAndNpcId(campaignId, npcId)) {
            throw new NpcAlreadyAssociatedException();
        }

        CampaignNpc assoc = campaignNpcRepository.save(CampaignNpc.builder()
                .campaign(campaign)
                .npcId(npc.getId())
                .visible(true)
                .build());

        return new CampaignNpcResponse(campaignId, npcId, assoc.isVisible());
    }

    // ---------- US-20: visibilidade ----------

    @Transactional
    public CampaignNpcResponse setVisibility(UUID campaignId, UUID npcId,
                                             UUID requesterId, boolean visible) {
        requireCampaignMaster(campaignId, requesterId);
        CampaignNpc assoc = campaignNpcRepository.findByCampaignIdAndNpcId(campaignId, npcId)
                .orElseThrow(NpcNotFoundException::new);

        assoc.setVisible(visible);
        campaignNpcRepository.save(assoc);
        return new CampaignNpcResponse(campaignId, npcId, assoc.isVisible());
    }

    /**
     * Remove um NPC de uma campanha (desassocia). Só o Mestre. Limpa em cascata os
     * agendamentos daquele NPC na campanha e os logs vinculados a eles. O NPC permanece
     * no acervo do dono e pode ser reassociado depois.
     */
    @Transactional
    public void removeFromCampaign(UUID campaignId, UUID npcId, UUID requesterId) {
        requireCampaignMaster(campaignId, requesterId);
        CampaignNpc assoc = campaignNpcRepository.findByCampaignIdAndNpcId(campaignId, npcId)
                .orElseThrow(NpcNotFoundException::new);

        List<Booking> bookings =
                bookingRepository.findByNpcIdAndTimeSkipDay_TimeSkip_CampaignId(npcId, campaignId);
        if (!bookings.isEmpty()) {
            logRepository.deleteByBookingIn(bookings);
            bookingRepository.deleteAll(bookings);
        }
        campaignNpcRepository.delete(assoc);
    }

    // ---------- acervo do dono ----------

    @Transactional(readOnly = true)
    public List<NpcSummaryResponse> listOwned(UUID ownerId) {
        return npcRepository.findByOwnerId(ownerId).stream()
                .map(n -> new NpcSummaryResponse(n.getId(), n.getName(), true,
                        npcImageRepository.existsById(n.getId()),
                        toInteractionDtos(n.getInteractions())))
                .toList();
    }

    // ---------- US-20: listar/consultar dentro da campanha ----------

    @Transactional(readOnly = true)
    public List<NpcSummaryResponse> listForCampaign(UUID campaignId, UUID requesterId) {
        Role role = requireMembership(campaignId, requesterId);

        List<CampaignNpc> associations = role == Role.MASTER
                ? campaignNpcRepository.findByCampaignId(campaignId)
                : campaignNpcRepository.findByCampaignIdAndVisibleTrue(campaignId);

        Map<UUID, Npc> npcById = npcRepository
                .findAllById(associations.stream().map(CampaignNpc::getNpcId).toList()).stream()
                .collect(Collectors.toMap(Npc::getId, Function.identity()));

        return associations.stream()
                .filter(a -> npcById.containsKey(a.getNpcId()))
                .map(a -> {
                    Npc npc = npcById.get(a.getNpcId());
                    return new NpcSummaryResponse(a.getNpcId(), npc.getName(),
                            a.isVisible(), npcImageRepository.existsById(a.getNpcId()),
                            toInteractionDtos(npc.getInteractions()));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public NpcResponse getForCampaign(UUID campaignId, UUID npcId, UUID requesterId) {
        Role role = requireMembership(campaignId, requesterId);
        CampaignNpc assoc = campaignNpcRepository.findByCampaignIdAndNpcId(campaignId, npcId)
                .orElseThrow(NpcNotFoundException::new);

        // Jogador não pode acessar NPC oculto — 404 para não revelar a existência
        if (!assoc.isVisible() && role != Role.MASTER) {
            throw new NpcNotFoundException();
        }
        Npc npc = npcRepository.findById(npcId).orElseThrow(NpcNotFoundException::new);
        return toResponse(npc, assoc.isVisible());
    }

    // ---------- imagem (retrato) do NPC ----------

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024; // 5 MB

    /** Define/atualiza a imagem do NPC. Só o dono. */
    @Transactional
    public void setImage(UUID npcId, UUID ownerId, byte[] data, String contentType) {
        Npc npc = npcRepository.findById(npcId).orElseThrow(NpcNotFoundException::new);
        requireOwner(npc, ownerId);

        if (data == null || data.length == 0) {
            throw new InvalidImageException("A imagem está vazia");
        }
        if (data.length > MAX_IMAGE_BYTES) {
            throw new InvalidImageException("A imagem excede o limite de 5 MB");
        }
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidImageException("O arquivo enviado não é uma imagem");
        }

        NpcImage image = npcImageRepository.findById(npcId).orElseGet(NpcImage::new);
        image.setNpcId(npcId);
        image.setContentType(contentType);
        image.setData(data);
        npcImageRepository.save(image);
    }

    /** Remove a imagem do NPC. Só o dono. */
    @Transactional
    public void deleteImage(UUID npcId, UUID ownerId) {
        Npc npc = npcRepository.findById(npcId).orElseThrow(NpcNotFoundException::new);
        requireOwner(npc, ownerId);
        npcImageRepository.deleteById(npcId);
    }

    /** Busca a imagem do NPC dentro de uma campanha (respeita a visibilidade). */
    @Transactional(readOnly = true)
    public NpcImage getImage(UUID campaignId, UUID npcId, UUID requesterId) {
        Role role = requireMembership(campaignId, requesterId);
        CampaignNpc assoc = campaignNpcRepository.findByCampaignIdAndNpcId(campaignId, npcId)
                .orElseThrow(NpcNotFoundException::new);
        if (!assoc.isVisible() && role != Role.MASTER) {
            throw new NpcNotFoundException();
        }
        return npcImageRepository.findById(npcId).orElseThrow(NpcNotFoundException::new);
    }

    // ---------- helpers de autorização ----------

    private void requireOwner(Npc npc, UUID requesterId) {
        if (!npc.getOwnerId().equals(requesterId)) {
            throw new AccessDeniedException("Apenas o dono do NPC pode realizar esta ação");
        }
    }

    private void requireCampaignMaster(UUID campaignId, UUID requesterId) {
        if (!memberRepository.existsByCampaignIdAndUserIdAndRole(campaignId, requesterId, Role.MASTER)) {
            throw new AccessDeniedException("Apenas o Mestre da campanha pode realizar esta ação");
        }
    }

    private Role requireMembership(UUID campaignId, UUID requesterId) {
        return memberRepository.findByCampaignIdAndUserId(campaignId, requesterId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"))
                .getRole();
    }

    // ---------- mapeamentos ----------

    private NpcAttributes toAttributes(NpcAttributesDto dto) {
        if (dto == null) return null;
        return NpcAttributes.builder()
                .forca(dto.forca())
                .destreza(dto.destreza())
                .constituicao(dto.constituicao())
                .mental(dto.mental())
                .inteligencia(dto.inteligencia())
                .talento(dto.talento())
                .build();
    }

    private NpcAttributesDto toAttributesDto(NpcAttributes a) {
        if (a == null) return null;
        return new NpcAttributesDto(a.getForca(), a.getDestreza(), a.getConstituicao(),
                a.getMental(), a.getInteligencia(), a.getTalento());
    }

    private List<NpcDetail> toDetails(List<NpcDetailDto> dtos) {
        if (dtos == null) return new ArrayList<>();
        return dtos.stream()
                .map(d -> new NpcDetail(d.name(), d.description()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private List<NpcDetailDto> toDetailDtos(List<NpcDetail> details) {
        return details.stream()
                .map(d -> new NpcDetailDto(d.getName(), d.getDescription()))
                .toList();
    }

    private List<NpcInteraction> toInteractions(List<NpcInteractionDto> dtos) {
        if (dtos == null) return new ArrayList<>();
        return dtos.stream()
                .map(d -> new NpcInteraction(d.type(), d.name(), d.description(),
                        d.idlePointCost() == null ? 0 : d.idlePointCost()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private List<NpcInteractionDto> toInteractionDtos(List<NpcInteraction> interactions) {
        return interactions.stream()
                .map(i -> new NpcInteractionDto(i.getType(), i.getName(), i.getDescription(),
                        i.getIdlePointCost()))
                .toList();
    }

    private NpcResponse toResponse(Npc npc, Boolean visible) {
        return new NpcResponse(
                npc.getId(),
                npc.getName(),
                npc.getDescription(),
                toAttributesDto(npc.getAttributes()),
                toDetailDtos(npc.getKnowledge()),
                toDetailDtos(npc.getTraits()),
                toDetailDtos(npc.getSpecs()),
                toInteractionDtos(npc.getInteractions()),
                npc.getOwnerId(),
                npcImageRepository.existsById(npc.getId()),
                visible);
    }
}
