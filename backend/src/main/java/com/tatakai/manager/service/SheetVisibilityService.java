package com.tatakai.manager.service;

import com.tatakai.manager.dto.response.SectionVisibilityResponse;
import com.tatakai.manager.entity.CampaignMember;
import com.tatakai.manager.entity.CharacterSheetVisibility;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.entity.SheetSection;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.MemberNotFoundException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CharacterSheetVisibilityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SheetVisibilityService {

    private final CharacterSheetVisibilityRepository visibilityRepository;
    private final CampaignMemberRepository memberRepository;

    public SheetVisibilityService(CharacterSheetVisibilityRepository visibilityRepository,
                                  CampaignMemberRepository memberRepository) {
        this.visibilityRepository = visibilityRepository;
        this.memberRepository = memberRepository;
    }

    /**
     * Oculta/exibe uma seção da ficha de um membro.
     * O Mestre ajusta a visibilidade para os jogadores (hiddenByMaster);
     * o próprio dono ajusta a sua (hiddenBySelf). Ninguém mais pode.
     */
    @Transactional
    public SectionVisibilityResponse setVisibility(UUID campaignId, UUID targetUserId,
                                                   SheetSection section, boolean hidden,
                                                   UUID requesterId) {
        CampaignMember requester = requireMember(campaignId, requesterId);
        CampaignMember target = memberRepository.findByCampaignIdAndUserId(campaignId, targetUserId)
                .orElseThrow(MemberNotFoundException::new);

        boolean isMaster = requester.getRole() == Role.MASTER;
        boolean isOwner = requesterId.equals(targetUserId);
        if (!isMaster && !isOwner) {
            throw new AccessDeniedException("Você só pode alterar a visibilidade da sua própria ficha");
        }

        CharacterSheetVisibility vis = visibilityRepository
                .findByCampaignMemberIdAndSection(target.getId(), section)
                .orElseGet(() -> CharacterSheetVisibility.builder()
                        .campaignMember(target).section(section).build());

        if (isMaster) {
            vis.setHiddenByMaster(hidden);
        } else {
            vis.setHiddenBySelf(hidden);
        }

        CharacterSheetVisibility saved = visibilityRepository.save(vis);
        return toResponse(saved.getSection(), saved.isHiddenByMaster(), saved.isHiddenBySelf(),
                true /* quem editou (Mestre ou dono) sempre vê */);
    }

    /** Visibilidade efetiva de cada seção da ficha do alvo, do ponto de vista de quem consulta. */
    @Transactional(readOnly = true)
    public List<SectionVisibilityResponse> getVisibility(UUID campaignId, UUID targetUserId,
                                                         UUID requesterId) {
        CampaignMember requester = requireMember(campaignId, requesterId);
        CampaignMember target = memberRepository.findByCampaignIdAndUserId(campaignId, targetUserId)
                .orElseThrow(MemberNotFoundException::new);

        boolean privileged = requester.getRole() == Role.MASTER || requesterId.equals(targetUserId);

        Map<SheetSection, CharacterSheetVisibility> bySection =
                visibilityRepository.findByCampaignMemberId(target.getId()).stream()
                        .collect(Collectors.toMap(CharacterSheetVisibility::getSection, Function.identity()));

        return java.util.Arrays.stream(SheetSection.values())
                .map(section -> {
                    CharacterSheetVisibility v = bySection.get(section);
                    boolean hiddenByMaster = v != null && v.isHiddenByMaster();
                    boolean hiddenBySelf = v != null && v.isHiddenBySelf();
                    boolean visibleToMe = privileged || (!hiddenByMaster && !hiddenBySelf);
                    return toResponse(section, hiddenByMaster, hiddenBySelf, visibleToMe);
                })
                .toList();
    }

    private CampaignMember requireMember(UUID campaignId, UUID userId) {
        return memberRepository.findByCampaignIdAndUserId(campaignId, userId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"));
    }

    private SectionVisibilityResponse toResponse(SheetSection section, boolean hiddenByMaster,
                                                 boolean hiddenBySelf, boolean visibleToMe) {
        return new SectionVisibilityResponse(section, hiddenByMaster, hiddenBySelf, visibleToMe);
    }
}
