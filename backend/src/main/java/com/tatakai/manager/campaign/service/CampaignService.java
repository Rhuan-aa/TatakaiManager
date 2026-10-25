package com.tatakai.manager.campaign.service;

import com.tatakai.manager.auth.entity.User;
import com.tatakai.manager.auth.exception.UserNotFoundException;
import com.tatakai.manager.auth.repository.UserRepository;
import com.tatakai.manager.campaign.dto.request.CreateCampaignRequest;
import com.tatakai.manager.campaign.dto.request.InviteMemberRequest;
import com.tatakai.manager.campaign.dto.response.CampaignResponse;
import com.tatakai.manager.campaign.dto.response.MemberResponse;
import com.tatakai.manager.campaign.entity.Campaign;
import com.tatakai.manager.campaign.entity.CampaignMember;
import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.exception.AlreadyMemberException;
import com.tatakai.manager.campaign.exception.CampaignNotFoundException;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.campaign.repository.CampaignRepository;
import com.tatakai.manager.shared.exception.AccessDeniedException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignMemberRepository memberRepository;
    private final UserRepository userRepository;

    public CampaignService(CampaignRepository campaignRepository,
                           CampaignMemberRepository memberRepository,
                           UserRepository userRepository) {
        this.campaignRepository = campaignRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CampaignResponse create(UUID creatorId, CreateCampaignRequest req) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new UserNotFoundException("Usuário autenticado não encontrado"));

        Campaign campaign = campaignRepository.save(Campaign.builder()
                .name(req.name())
                .description(req.description())
                .master(creator)
                .build());

        // O criador entra como membro MASTER da própria campanha
        memberRepository.save(CampaignMember.builder()
                .campaign(campaign)
                .user(creator)
                .role(Role.MASTER)
                .build());

        return new CampaignResponse(campaign.getId(), campaign.getName(),
                campaign.getDescription(), Role.MASTER);
    }

    @Transactional
    public MemberResponse invite(UUID campaignId, UUID requesterId, InviteMemberRequest req) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(CampaignNotFoundException::new);

        // Apenas o Mestre da campanha pode convidar
        if (!campaign.getMaster().getId().equals(requesterId)) {
            throw new AccessDeniedException("Apenas o Mestre pode convidar jogadores");
        }

        User invited = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new UserNotFoundException(
                        "Nenhum usuário cadastrado com o e-mail: " + req.email()));

        if (memberRepository.existsByCampaignIdAndUserId(campaignId, invited.getId())) {
            throw new AlreadyMemberException();
        }

        memberRepository.save(CampaignMember.builder()
                .campaign(campaign)
                .user(invited)
                .role(Role.PLAYER)
                .build());

        return new MemberResponse(invited.getId(), invited.getName(),
                invited.getEmail(), Role.PLAYER);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(UUID campaignId, UUID requesterId) {
        if (!memberRepository.existsByCampaignIdAndUserId(campaignId, requesterId)) {
            throw new AccessDeniedException("Você não é membro desta campanha");
        }
        return memberRepository.findByCampaignId(campaignId).stream()
                .map(m -> new MemberResponse(m.getUser().getId(), m.getUser().getName(),
                        m.getUser().getEmail(), m.getRole()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CampaignResponse> listMyCampaigns(UUID userId) {
        return memberRepository.findByUserId(userId).stream()
                .map(m -> {
                    Campaign c = m.getCampaign();
                    return new CampaignResponse(c.getId(), c.getName(),
                            c.getDescription(), m.getRole());
                })
                .toList();
    }
}
