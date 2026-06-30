package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.CreateNpcRequest;
import com.tatakai.manager.dto.request.NpcAttributesDto;
import com.tatakai.manager.dto.request.SpecDto;
import com.tatakai.manager.dto.request.UpdateNpcRequest;
import com.tatakai.manager.dto.response.CampaignNpcResponse;
import com.tatakai.manager.dto.response.NpcResponse;
import com.tatakai.manager.dto.response.NpcSummaryResponse;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.CampaignNotFoundException;
import com.tatakai.manager.exception.NpcAlreadyAssociatedException;
import com.tatakai.manager.exception.NpcNotFoundException;
import com.tatakai.manager.exception.UserNotFoundException;
import com.tatakai.manager.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class NpcService {

    private final NpcRepository npcRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignNpcRepository campaignNpcRepository;
    private final CampaignMemberRepository memberRepository;
    private final UserRepository userRepository;

    public NpcService(NpcRepository npcRepository,
                      CampaignRepository campaignRepository,
                      CampaignNpcRepository campaignNpcRepository,
                      CampaignMemberRepository memberRepository,
                      UserRepository userRepository) {
        this.npcRepository = npcRepository;
        this.campaignRepository = campaignRepository;
        this.campaignNpcRepository = campaignNpcRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    // ---------- US-06: criar ----------

    @Transactional
    public NpcResponse create(UUID ownerId, CreateNpcRequest req) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new UserNotFoundException("Usuário autenticado não encontrado"));

        Npc npc = Npc.builder()
                .name(req.name())
                .description(req.description())
                .attributes(toAttributes(req.attributes()))
                .owner(owner)
                .specs(toSpecs(req.specs()))
                .traits(req.traits() == null ? new ArrayList<>() : new ArrayList<>(req.traits()))
                .interactionTypes(new HashSet<>(req.interactionTypes()))
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
        npc.getSpecs().clear();
        npc.getSpecs().addAll(toSpecs(req.specs()));
        npc.getTraits().clear();
        if (req.traits() != null) npc.getTraits().addAll(req.traits());
        npc.getInteractionTypes().clear();
        npc.getInteractionTypes().addAll(req.interactionTypes());

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
                .npc(npc)
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

    // ---------- acervo do dono ----------

    @Transactional(readOnly = true)
    public List<NpcSummaryResponse> listOwned(UUID ownerId) {
        return npcRepository.findByOwnerId(ownerId).stream()
                .map(n -> new NpcSummaryResponse(n.getId(), n.getName(), true, n.getInteractionTypes()))
                .toList();
    }

    // ---------- US-20: listar/consultar dentro da campanha ----------

    @Transactional(readOnly = true)
    public List<NpcSummaryResponse> listForCampaign(UUID campaignId, UUID requesterId) {
        Role role = requireMembership(campaignId, requesterId);

        List<CampaignNpc> associations = role == Role.MASTER
                ? campaignNpcRepository.findByCampaignId(campaignId)
                : campaignNpcRepository.findByCampaignIdAndVisibleTrue(campaignId);

        return associations.stream()
                .map(a -> new NpcSummaryResponse(a.getNpc().getId(), a.getNpc().getName(),
                        a.isVisible(), a.getNpc().getInteractionTypes()))
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
        return toResponse(assoc.getNpc(), assoc.isVisible());
    }

    // ---------- helpers de autorização ----------

    private void requireOwner(Npc npc, UUID requesterId) {
        if (!npc.getOwner().getId().equals(requesterId)) {
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

    private List<NpcSpec> toSpecs(List<SpecDto> dtos) {
        if (dtos == null) return new ArrayList<>();
        return dtos.stream()
                .map(d -> new NpcSpec(d.name(), d.description()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private List<SpecDto> toSpecDtos(List<NpcSpec> specs) {
        return specs.stream().map(s -> new SpecDto(s.getName(), s.getDescription())).toList();
    }

    private NpcResponse toResponse(Npc npc, Boolean visible) {
        return new NpcResponse(
                npc.getId(),
                npc.getName(),
                npc.getDescription(),
                toAttributesDto(npc.getAttributes()),
                toSpecDtos(npc.getSpecs()),
                List.copyOf(npc.getTraits()),
                npc.getInteractionTypes(),
                npc.getOwner().getId(),
                visible);
    }
}
