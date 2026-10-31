package com.tatakai.manager.npc.service;

import com.tatakai.manager.auth.exception.UserNotFoundException;
import com.tatakai.manager.auth.repository.UserRepository;
import com.tatakai.manager.campaign.dto.response.CampaignUpdateMessage;
import com.tatakai.manager.campaign.entity.Campaign;
import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.exception.CampaignNotFoundException;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.campaign.repository.CampaignRepository;
import com.tatakai.manager.log.repository.InteractionLogRepository;
import com.tatakai.manager.npc.dto.request.CreateNpcRequest;
import com.tatakai.manager.npc.dto.request.NpcDetailDto;
import com.tatakai.manager.npc.dto.request.NpcInteractionDto;
import com.tatakai.manager.npc.dto.request.UpdateNpcRequest;
import com.tatakai.manager.npc.dto.response.CampaignNpcResponse;
import com.tatakai.manager.npc.dto.response.NpcResponse;
import com.tatakai.manager.npc.dto.response.NpcSummaryResponse;
import com.tatakai.manager.npc.entity.CampaignNpc;
import com.tatakai.manager.npc.entity.Npc;
import com.tatakai.manager.npc.entity.NpcAttributes;
import com.tatakai.manager.npc.entity.NpcDetail;
import com.tatakai.manager.npc.entity.NpcImage;
import com.tatakai.manager.npc.entity.NpcInteraction;
import com.tatakai.manager.npc.exception.InvalidImageException;
import com.tatakai.manager.npc.exception.NpcAlreadyAssociatedException;
import com.tatakai.manager.npc.exception.NpcNotFoundException;
import com.tatakai.manager.npc.repository.CampaignNpcRepository;
import com.tatakai.manager.npc.repository.NpcImageRepository;
import com.tatakai.manager.npc.repository.NpcRepository;
import com.tatakai.manager.shared.exception.AccessDeniedException;
import com.tatakai.manager.sheet.entity.CharacterSheet;
import com.tatakai.manager.sheet.repository.CharacterSheetRepository;
import com.tatakai.manager.sheet.repository.NpcSheetVisibilityRepository;
import com.tatakai.manager.timeskip.entity.Booking;
import com.tatakai.manager.timeskip.repository.BookingRepository;

import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
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
    private final CharacterSheetRepository sheetRepository;
    private final NpcSheetVisibilityRepository npcSheetVisibilityRepository;

    private final ApplicationEventPublisher events;

    public NpcService(NpcRepository npcRepository,
                      CampaignRepository campaignRepository,
                      CampaignNpcRepository campaignNpcRepository,
                      CampaignMemberRepository memberRepository,
                      UserRepository userRepository,
                      NpcImageRepository npcImageRepository,
                      BookingRepository bookingRepository,
                      InteractionLogRepository logRepository,
                      CharacterSheetRepository sheetRepository,
                      NpcSheetVisibilityRepository npcSheetVisibilityRepository,
                      ApplicationEventPublisher events) {
        this.events = events;
        this.npcRepository = npcRepository;
        this.campaignRepository = campaignRepository;
        this.campaignNpcRepository = campaignNpcRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.npcImageRepository = npcImageRepository;
        this.bookingRepository = bookingRepository;
        this.logRepository = logRepository;
        this.sheetRepository = sheetRepository;
        this.npcSheetVisibilityRepository = npcSheetVisibilityRepository;
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
                .ownerId(ownerId)
                .knowledge(toDetails(req.knowledge()))
                .specs(toDetails(req.specs()))
                .interactions(toInteractions(req.interactions()))
                .createdAt(Instant.now())
                .build();

        Npc saved = npcRepository.save(npc);
        // §51.8: todo NPC tem uma única ficha; nasce leve (sem atributos/recursos) e é completada depois
        sheetRepository.save(CharacterSheet.builder().npcId(saved.getId()).npcOwnerId(ownerId).name(saved.getName())
                .buildStatus(com.tatakai.manager.build.entity.BuildStatus.COMPLETED).combatReady(false).build());
        return toResponse(saved, null);
    }

    // ---------- US-07: editar ----------

    @Transactional
    public NpcResponse update(UUID npcId, UUID requesterId, UpdateNpcRequest req) {
        Npc npc = npcRepository.findById(npcId).orElseThrow(NpcNotFoundException::new);
        requireOwner(npc, requesterId);

        npc.setName(req.name());
        sheetRepository.findByNpcId(npcId).ifPresent(sh -> { sh.setName(req.name()); sheetRepository.save(sh); });
        npc.setDescription(req.description());
        npc.getKnowledge().clear();
        npc.getKnowledge().addAll(toDetails(req.knowledge()));
        npc.getSpecs().clear();
        npc.getSpecs().addAll(toDetails(req.specs()));
        npc.getInteractions().clear();
        npc.getInteractions().addAll(toInteractions(req.interactions()));

        NpcResponse out = toResponse(npcRepository.save(npc), null);
        campaignNpcRepository.findByNpcId(npcId).forEach(a -> notifyNpcs(a.getCampaign().getId(), npcId));
        return out;
    }

    /** Avisa os clientes da campanha que a lista de NPCs mudou (após o commit). */
    private void notifyNpcs(UUID campaignId, UUID npcId) {
        events.publishEvent(new CampaignUpdateMessage(campaignId, "NPCS", npcId));
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

        notifyNpcs(campaignId, npcId);
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
        notifyNpcs(campaignId, npcId);
        return new CampaignNpcResponse(campaignId, npcId, assoc.isVisible());
    }

    /** NPC só de ficha/combate: fora da Agenda da campanha. Só o Mestre. */
    @Transactional
    public CampaignNpcResponse setBookable(UUID campaignId, UUID npcId, UUID requesterId, boolean bookable) {
        requireCampaignMaster(campaignId, requesterId);
        CampaignNpc assoc = campaignNpcRepository.findByCampaignIdAndNpcId(campaignId, npcId)
                .orElseThrow(NpcNotFoundException::new);
        assoc.setBookable(bookable);
        campaignNpcRepository.save(assoc);
        notifyNpcs(campaignId, npcId);
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
        npcSheetVisibilityRepository.deleteByCampaignNpcId(assoc.getId());
        campaignNpcRepository.delete(assoc);
        notifyNpcs(campaignId, npcId);
    }

    // ---------- acervo do dono ----------

    @Transactional(readOnly = true)
    public List<NpcSummaryResponse> listOwned(UUID ownerId) {
        List<Npc> npcs = npcRepository.findByOwnerId(ownerId);
        Map<UUID, CharacterSheet> sheets = sheetsByNpc(npcs.stream().map(Npc::getId).toList());
        return npcs.stream()
                .map(n -> new NpcSummaryResponse(n.getId(), n.getName(), true,
                        npcImageRepository.existsById(n.getId()),
                        toInteractionDtos(n.getInteractions()), sheetIdOf(sheets, n.getId()), true,
                        readyOf(sheets, n.getId())))
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

        Map<UUID, CharacterSheet> sheets = sheetsByNpc(npcById.keySet());
        return associations.stream()
                .filter(a -> npcById.containsKey(a.getNpcId()))
                .map(a -> {
                    Npc npc = npcById.get(a.getNpcId());
                    return new NpcSummaryResponse(a.getNpcId(), npc.getName(),
                            a.isVisible(), npcImageRepository.existsById(a.getNpcId()),
                            toInteractionDtos(npc.getInteractions()), sheetIdOf(sheets, a.getNpcId()), a.isBookable(),
                            readyOf(sheets, a.getNpcId()));
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

    private Map<UUID, CharacterSheet> sheetsByNpc(java.util.Collection<UUID> npcIds) {
        return sheetRepository.findByNpcIdIn(npcIds).stream()
                .collect(Collectors.toMap(CharacterSheet::getNpcId, Function.identity(), (a, b) -> a));
    }

    private static UUID sheetIdOf(Map<UUID, CharacterSheet> sheets, UUID npcId) {
        CharacterSheet s = sheets.get(npcId);
        return s == null ? null : s.getId();
    }

    private static boolean readyOf(Map<UUID, CharacterSheet> sheets, UUID npcId) {
        CharacterSheet s = sheets.get(npcId);
        return s != null && s.isCombatReady();
    }

    private NpcResponse toResponse(Npc npc, Boolean visible) {
        return new NpcResponse(
                npc.getId(),
                npc.getName(),
                npc.getDescription(),
                toDetailDtos(npc.getKnowledge()),
                toDetailDtos(npc.getSpecs()),
                toInteractionDtos(npc.getInteractions()),
                npc.getOwnerId(),
                npcImageRepository.existsById(npc.getId()),
                visible,
                sheetRepository.findByNpcId(npc.getId()).map(CharacterSheet::getId).orElse(null),
                sheetRepository.findByNpcId(npc.getId()).map(CharacterSheet::isCombatReady).orElse(false));
    }
}
