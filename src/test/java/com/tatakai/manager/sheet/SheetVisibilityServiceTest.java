package com.tatakai.manager.sheet;

import com.tatakai.manager.dto.response.SectionVisibilityResponse;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.MemberNotFoundException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CharacterSheetVisibilityRepository;
import com.tatakai.manager.service.SheetVisibilityService;
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
@DisplayName("SheetVisibilityService — US-21, US-22")
class SheetVisibilityServiceTest {

    @Mock private CharacterSheetVisibilityRepository visibilityRepository;
    @Mock private CampaignMemberRepository memberRepository;

    private SheetVisibilityService service;

    private UUID campaignId;
    private User master;
    private User owner;   // dono da ficha (jogador)
    private User other;   // outro jogador
    private CampaignMember ownerMember;

    @BeforeEach
    void setUp() {
        service = new SheetVisibilityService(visibilityRepository, memberRepository);
        campaignId = UUID.randomUUID();
        master = User.builder().id(UUID.randomUUID()).name("Mestre").build();
        owner = User.builder().id(UUID.randomUUID()).name("Ana").build();
        other = User.builder().id(UUID.randomUUID()).name("Bruno").build();
        Campaign campaign = Campaign.builder().id(campaignId).name("Aether").master(master).build();
        ownerMember = CampaignMember.builder()
                .id(UUID.randomUUID()).campaign(campaign).user(owner).role(Role.PLAYER).build();
    }

    private void mockRole(User user, Role role) {
        when(memberRepository.findByCampaignIdAndUserId(campaignId, user.getId()))
                .thenReturn(Optional.of(CampaignMember.builder()
                        .campaign(ownerMember.getCampaign()).user(user).role(role).build()));
    }

    private void mockTargetMember() {
        when(memberRepository.findByCampaignIdAndUserId(campaignId, owner.getId()))
                .thenReturn(Optional.of(ownerMember));
    }

    @Test
    @DisplayName("US-21: Mestre oculta uma seção da ficha de um jogador (hiddenByMaster)")
    void masterHidesPlayerSection() {
        mockRole(master, Role.MASTER);          // requester
        mockTargetMember();                       // alvo = ownerMember
        when(visibilityRepository.findByCampaignMemberIdAndSection(ownerMember.getId(), SheetSection.LOGS))
                .thenReturn(Optional.empty());
        when(visibilityRepository.save(any(CharacterSheetVisibility.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SectionVisibilityResponse res = service.setVisibility(
                campaignId, owner.getId(), SheetSection.LOGS, true, master.getId());

        assertThat(res.hiddenByMaster()).isTrue();
        ArgumentCaptor<CharacterSheetVisibility> captor =
                ArgumentCaptor.forClass(CharacterSheetVisibility.class);
        verify(visibilityRepository).save(captor.capture());
        assertThat(captor.getValue().isHiddenByMaster()).isTrue();
        assertThat(captor.getValue().isHiddenBySelf()).isFalse();
    }

    @Test
    @DisplayName("US-22: jogador oculta uma seção da própria ficha (hiddenBySelf)")
    void ownerHidesOwnSection() {
        mockRole(owner, Role.PLAYER);
        mockTargetMember();
        when(visibilityRepository.findByCampaignMemberIdAndSection(ownerMember.getId(), SheetSection.LOGS))
                .thenReturn(Optional.empty());
        when(visibilityRepository.save(any(CharacterSheetVisibility.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SectionVisibilityResponse res = service.setVisibility(
                campaignId, owner.getId(), SheetSection.LOGS, true, owner.getId());

        assertThat(res.hiddenBySelf()).isTrue();
        assertThat(res.hiddenByMaster()).isFalse();
    }

    @Test
    @DisplayName("jogador não pode alterar a visibilidade da ficha de outro — AccessDenied")
    void otherPlayerCannotEditSomeoneElsesSheet() {
        mockRole(other, Role.PLAYER);
        mockTargetMember();

        assertThatThrownBy(() -> service.setVisibility(
                campaignId, owner.getId(), SheetSection.LOGS, true, other.getId()))
                .isInstanceOf(AccessDeniedException.class);

        verify(visibilityRepository, never()).save(any());
    }

    @Test
    @DisplayName("não-membro não pode alterar visibilidade — AccessDenied")
    void nonMemberCannotEdit() {
        when(memberRepository.findByCampaignIdAndUserId(campaignId, other.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setVisibility(
                campaignId, owner.getId(), SheetSection.LOGS, true, other.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("alvo que não é membro da campanha resulta em 404")
    void targetNotMember_throwsNotFound() {
        mockRole(master, Role.MASTER);
        when(memberRepository.findByCampaignIdAndUserId(campaignId, owner.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setVisibility(
                campaignId, owner.getId(), SheetSection.LOGS, true, master.getId()))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    @DisplayName("US-21: outro jogador NÃO vê seção que o Mestre ocultou")
    void otherPlayerDoesNotSeeMasterHiddenSection() {
        mockRole(other, Role.PLAYER);
        mockTargetMember();
        var hidden = CharacterSheetVisibility.builder()
                .campaignMember(ownerMember).section(SheetSection.LOGS)
                .hiddenByMaster(true).hiddenBySelf(false).build();
        when(visibilityRepository.findByCampaignMemberId(ownerMember.getId()))
                .thenReturn(List.of(hidden));

        List<SectionVisibilityResponse> res =
                service.getVisibility(campaignId, owner.getId(), other.getId());

        SectionVisibilityResponse logs = res.stream()
                .filter(s -> s.section() == SheetSection.LOGS).findFirst().orElseThrow();
        assertThat(logs.visibleToMe()).isFalse();
    }

    @Test
    @DisplayName("US-22: o dono sempre vê a própria seção, mesmo oculta")
    void ownerAlwaysSeesOwnSection() {
        mockRole(owner, Role.PLAYER);
        mockTargetMember();
        var hidden = CharacterSheetVisibility.builder()
                .campaignMember(ownerMember).section(SheetSection.LOGS)
                .hiddenByMaster(false).hiddenBySelf(true).build();
        when(visibilityRepository.findByCampaignMemberId(ownerMember.getId()))
                .thenReturn(List.of(hidden));

        List<SectionVisibilityResponse> res =
                service.getVisibility(campaignId, owner.getId(), owner.getId());

        SectionVisibilityResponse logs = res.stream()
                .filter(s -> s.section() == SheetSection.LOGS).findFirst().orElseThrow();
        assertThat(logs.visibleToMe()).isTrue();
    }

    @Test
    @DisplayName("US-21: o Mestre sempre vê todas as seções")
    void masterSeesEverything() {
        mockRole(master, Role.MASTER);
        mockTargetMember();
        var hidden = CharacterSheetVisibility.builder()
                .campaignMember(ownerMember).section(SheetSection.LOGS)
                .hiddenByMaster(true).hiddenBySelf(true).build();
        when(visibilityRepository.findByCampaignMemberId(ownerMember.getId()))
                .thenReturn(List.of(hidden));

        List<SectionVisibilityResponse> res =
                service.getVisibility(campaignId, owner.getId(), master.getId());

        assertThat(res).allSatisfy(s -> assertThat(s.visibleToMe()).isTrue());
        assertThat(res).hasSize(SheetSection.values().length);
    }
}
