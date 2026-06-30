package com.tatakai.manager.campaign;

import com.tatakai.manager.dto.request.CreateCampaignRequest;
import com.tatakai.manager.dto.request.InviteMemberRequest;
import com.tatakai.manager.dto.response.CampaignResponse;
import com.tatakai.manager.dto.response.MemberResponse;
import com.tatakai.manager.entity.Campaign;
import com.tatakai.manager.entity.CampaignMember;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.entity.User;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.AlreadyMemberException;
import com.tatakai.manager.exception.CampaignNotFoundException;
import com.tatakai.manager.exception.UserNotFoundException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CampaignRepository;
import com.tatakai.manager.repository.UserRepository;
import com.tatakai.manager.service.CampaignService;
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
@DisplayName("CampaignService — Sprint 2 (US-03, US-04, US-05)")
class CampaignServiceTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private UserRepository userRepository;

    private CampaignService service;

    private User master;
    private User player;

    @BeforeEach
    void setUp() {
        service = new CampaignService(campaignRepository, memberRepository, userRepository);
        master = User.builder().id(UUID.randomUUID()).name("Mestre").email("mestre@rpg.com").build();
        player = User.builder().id(UUID.randomUUID()).name("Player").email("player@rpg.com").build();
    }

    @Test
    @DisplayName("US-03: criar campanha registra o criador como membro MASTER")
    void createCampaign_registersCreatorAsMaster() {
        var req = new CreateCampaignRequest("Crônicas de Aether", "Uma campanha sombria");
        when(userRepository.findById(master.getId())).thenReturn(Optional.of(master));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> {
            Campaign c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CampaignResponse res = service.create(master.getId(), req);

        assertThat(res.name()).isEqualTo("Crônicas de Aether");
        assertThat(res.currentUserRole()).isEqualTo(Role.MASTER);

        ArgumentCaptor<CampaignMember> captor = ArgumentCaptor.forClass(CampaignMember.class);
        verify(memberRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.MASTER);
        assertThat(captor.getValue().getUser()).isEqualTo(master);
    }

    @Test
    @DisplayName("US-04: Mestre convida jogador existente por e-mail (entra como PLAYER)")
    void invite_byMaster_addsPlayer() {
        Campaign campaign = Campaign.builder()
                .id(UUID.randomUUID()).name("Aether").master(master).build();
        var req = new InviteMemberRequest("player@rpg.com");

        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(userRepository.findByEmail("player@rpg.com")).thenReturn(Optional.of(player));
        when(memberRepository.existsByCampaignIdAndUserId(campaign.getId(), player.getId()))
                .thenReturn(false);

        MemberResponse res = service.invite(campaign.getId(), master.getId(), req);

        assertThat(res.email()).isEqualTo("player@rpg.com");
        assertThat(res.role()).isEqualTo(Role.PLAYER);

        ArgumentCaptor<CampaignMember> captor = ArgumentCaptor.forClass(CampaignMember.class);
        verify(memberRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.PLAYER);
    }

    @Test
    @DisplayName("US-04: jogador (não-mestre) não pode convidar — AccessDenied")
    void invite_byNonMaster_throwsAccessDenied() {
        Campaign campaign = Campaign.builder()
                .id(UUID.randomUUID()).name("Aether").master(master).build();

        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() ->
                service.invite(campaign.getId(), player.getId(), new InviteMemberRequest("x@rpg.com")))
                .isInstanceOf(AccessDeniedException.class);

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-04: convite para e-mail inexistente é rejeitado")
    void invite_unknownEmail_throws() {
        Campaign campaign = Campaign.builder()
                .id(UUID.randomUUID()).name("Aether").master(master).build();
        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(userRepository.findByEmail("ghost@rpg.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.invite(campaign.getId(), master.getId(), new InviteMemberRequest("ghost@rpg.com")))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("US-04: convidar quem já é membro é rejeitado")
    void invite_alreadyMember_throws() {
        Campaign campaign = Campaign.builder()
                .id(UUID.randomUUID()).name("Aether").master(master).build();
        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(userRepository.findByEmail("player@rpg.com")).thenReturn(Optional.of(player));
        when(memberRepository.existsByCampaignIdAndUserId(campaign.getId(), player.getId()))
                .thenReturn(true);

        assertThatThrownBy(() ->
                service.invite(campaign.getId(), master.getId(), new InviteMemberRequest("player@rpg.com")))
                .isInstanceOf(AlreadyMemberException.class);

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-04: convidar para campanha inexistente é rejeitado")
    void invite_unknownCampaign_throws() {
        UUID ghostId = UUID.randomUUID();
        when(campaignRepository.findById(ghostId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.invite(ghostId, master.getId(), new InviteMemberRequest("player@rpg.com")))
                .isInstanceOf(CampaignNotFoundException.class);
    }

    @Test
    @DisplayName("US-05: jogador participa de múltiplas campanhas com o mesmo login")
    void listMyCampaigns_returnsAllMemberships() {
        Campaign c1 = Campaign.builder().id(UUID.randomUUID()).name("Aether").master(master).build();
        Campaign c2 = Campaign.builder().id(UUID.randomUUID()).name("Queda dos Deuses").master(master).build();
        var m1 = CampaignMember.builder().campaign(c1).user(player).role(Role.PLAYER).build();
        var m2 = CampaignMember.builder().campaign(c2).user(player).role(Role.PLAYER).build();

        when(memberRepository.findByUserId(player.getId())).thenReturn(List.of(m1, m2));

        List<CampaignResponse> result = service.listMyCampaigns(player.getId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CampaignResponse::name)
                .containsExactlyInAnyOrder("Aether", "Queda dos Deuses");
        assertThat(result).allSatisfy(c -> assertThat(c.currentUserRole()).isEqualTo(Role.PLAYER));
    }
}
