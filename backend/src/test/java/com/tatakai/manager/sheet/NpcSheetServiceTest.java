package com.tatakai.manager.sheet;

import com.tatakai.manager.dto.request.AttributesPayload;
import com.tatakai.manager.dto.request.CreateCharacterSheetRequest;
import com.tatakai.manager.dto.request.GmAdjustmentRequest;
import com.tatakai.manager.dto.request.SetNpcSectionVisibilityRequest;
import com.tatakai.manager.dto.request.UpdateCharacterSheetRequest;
import com.tatakai.manager.dto.response.CharacterSheetResponse;
import com.tatakai.manager.dto.response.NpcSectionVisibilityResponse;
import com.tatakai.manager.dto.response.SheetUpdateMessage;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.exception.InvalidCharacterSheetException;
import com.tatakai.manager.exception.NpcNotFoundException;
import com.tatakai.manager.exception.NpcSheetAlreadyExistsException;
import com.tatakai.manager.repository.*;
import com.tatakai.manager.service.CharacterSheetService;
import com.tatakai.manager.service.SheetActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CharacterSheetService (NPC) — Sprint 9 — F1.1 Ficha de NPC")
class NpcSheetServiceTest {

    @Mock private CharacterSheetRepository sheetRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private CharacterSheetVisibilityRepository visibilityRepository;
    @Mock private SheetActivityLogService activityLog;
    @Mock private ApplicationEventPublisher events;
    @Mock private CampaignNpcRepository campaignNpcRepository;
    @Mock private NpcSheetVisibilityRepository npcVisibilityRepository;
    @Mock private NpcRepository npcRepository;
    @Mock private UserRepository userRepository;
    @Mock private com.tatakai.manager.service.CampaignRuleService ruleService;
    @Mock private com.tatakai.manager.service.CatalogService catalogService;
    @Mock private com.tatakai.manager.repository.CharacterBuildRepository buildRepository;

    private CharacterSheetService service;
    private User owner;
    private User stranger;
    private User playerUser;
    private Campaign campaign;
    private Npc npc;
    private CampaignNpc cn;
    private CharacterSheet sheet;

    @BeforeEach
    void setUp() {
        service = new CharacterSheetService(sheetRepository, memberRepository, visibilityRepository,
                activityLog, events, campaignNpcRepository, npcVisibilityRepository, npcRepository, userRepository,
                ruleService, catalogService, buildRepository);
        owner = User.builder().id(UUID.randomUUID()).name("Mestre").build();
        stranger = User.builder().id(UUID.randomUUID()).name("Intruso").build();
        playerUser = User.builder().id(UUID.randomUUID()).name("Ana").build();
        campaign = Campaign.builder().id(UUID.randomUUID()).name("Mesa").build();
        npc = Npc.builder().id(UUID.randomUUID()).name("Aldric").ownerId(owner.getId()).build();
        cn = CampaignNpc.builder().id(UUID.randomUUID()).campaign(campaign).npcId(npc.getId()).visible(true).build();
        sheet = CharacterSheet.builder().id(UUID.randomUUID()).npcId(npc.getId()).npcOwnerId(owner.getId())
                .name("Aldric").buildStatus(BuildStatus.COMPLETED).bodyType(BodyType.MESOMORPH)
                .dominance(Dominance.RIGHT).heightM(1.8).weightKg(75)
                .str(10).dex(10).con(12).intl(10).cha(10).men(10).tal(10)
                .hpMax(22).spMax(11).kpMax(4).currentHp(22).currentSp(11).currentKp(4)
                .injuries(new ArrayList<>()).inventory(new ArrayList<>()).build();
        lenient().when(sheetRepository.findById(sheet.getId())).thenReturn(Optional.of(sheet));
        lenient().when(sheetRepository.findByNpcId(npc.getId())).thenReturn(Optional.of(sheet));
        lenient().when(sheetRepository.save(any(CharacterSheet.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
    }

    private CampaignMember member(User u, Role role) {
        CampaignMember m = CampaignMember.builder().id(UUID.randomUUID()).campaign(campaign).user(u).role(role).build();
        lenient().when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), u.getId()))
                .thenReturn(Optional.of(m));
        return m;
    }

    private CreateCharacterSheetRequest createReq() {
        return new CreateCharacterSheetRequest("Aldric", BodyType.MESOMORPH, Dominance.RIGHT, 1.8, null,
                new AttributesPayload(10, 10, 12, 10, 10, 10, 10));
    }

    private UpdateCharacterSheetRequest updateReq() {
        return new UpdateCharacterSheetRequest("Aldric II", BodyType.MESOMORPH, Dominance.RIGHT, 1.8, null,
                new AttributesPayload(10, 10, 12, 10, 10, 10, 10));
    }

    private void linkCampaignNpc() {
        lenient().when(campaignNpcRepository.findByCampaignIdAndNpcId(campaign.getId(), npc.getId()))
                .thenReturn(Optional.of(cn));
    }

    // ------------------------------------------------------------------ createForNpc

    @Test
    @DisplayName("createForNpc: dono cria ficha COMPLETED com npcId/npcOwnerId, log e SHEET_CREATED por campanha")
    void createForNpc_ok() {
        Campaign c2 = Campaign.builder().id(UUID.randomUUID()).build();
        CampaignNpc cn2 = CampaignNpc.builder().id(UUID.randomUUID()).campaign(c2).npcId(npc.getId()).build();
        when(npcRepository.findById(npc.getId())).thenReturn(Optional.of(npc));
        when(sheetRepository.existsByNpcId(npc.getId())).thenReturn(false);
        when(campaignNpcRepository.findByNpcId(npc.getId())).thenReturn(List.of(cn, cn2));

        CharacterSheetResponse r = service.createForNpc(npc.getId(), createReq(), owner.getId());

        assertThat(r.buildStatus()).isEqualTo(BuildStatus.COMPLETED);
        assertThat(r.npcId()).isEqualTo(npc.getId());
        assertThat(r.campaignId()).isNull();
        assertThat(r.ownerUserId()).isEqualTo(owner.getId());
        assertThat(r.resources().hpMax()).isEqualTo(22);
        assertThat(r.resources().spMax()).isEqualTo(11);
        assertThat(r.resources().kpMax()).isEqualTo(4);
        ArgumentCaptor<CharacterSheet> saved = ArgumentCaptor.forClass(CharacterSheet.class);
        verify(sheetRepository).save(saved.capture());
        assertThat(saved.getValue().getNpcId()).isEqualTo(npc.getId());
        assertThat(saved.getValue().getNpcOwnerId()).isEqualTo(owner.getId());
        verify(activityLog).record(any(CharacterSheet.class), eq(owner.getId()), eq(ActorRole.MASTER),
                eq(ActivityCategory.BUILD), eq("CREATE_SHEET"), any(), any(), any(), any(), any());
        ArgumentCaptor<SheetUpdateMessage> ev = ArgumentCaptor.forClass(SheetUpdateMessage.class);
        verify(events, times(2)).publishEvent(ev.capture());
        assertThat(ev.getAllValues()).extracting(SheetUpdateMessage::campaignId)
                .containsExactlyInAnyOrder(campaign.getId(), c2.getId());
        assertThat(ev.getAllValues()).allMatch(m -> m.event() == SheetUpdateMessage.SheetEvent.SHEET_CREATED);
    }

    @Test
    @DisplayName("createForNpc: NPC inexistente -> NpcNotFoundException")
    void createForNpc_npcMissing() {
        when(npcRepository.findById(npc.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createForNpc(npc.getId(), createReq(), owner.getId()))
                .isInstanceOf(NpcNotFoundException.class);
    }

    @Test
    @DisplayName("createForNpc: quem não é dono -> AccessDeniedException")
    void createForNpc_nonOwner() {
        when(npcRepository.findById(npc.getId())).thenReturn(Optional.of(npc));
        assertThatThrownBy(() -> service.createForNpc(npc.getId(), createReq(), stranger.getId()))
                .isInstanceOf(AccessDeniedException.class);
        verify(sheetRepository, never()).save(any());
    }

    @Test
    @DisplayName("createForNpc: NPC que já tem ficha -> NpcSheetAlreadyExistsException")
    void createForNpc_existing() {
        when(npcRepository.findById(npc.getId())).thenReturn(Optional.of(npc));
        when(sheetRepository.existsByNpcId(npc.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.createForNpc(npc.getId(), createReq(), owner.getId()))
                .isInstanceOf(NpcSheetAlreadyExistsException.class);
    }

    // ------------------------------------------------------------------ acesso via /character-sheets

    @Test
    @DisplayName("ficha de NPC: não-dono recebe CharacterSheetNotFoundException em get/update/gm-adjustment")
    void npcSheet_nonOwner_notFound() {
        GmAdjustmentRequest gm = new GmAdjustmentRequest(GmAction.ADJUST_MONEY, null, 5, "x", null, null, null);
        assertThatThrownBy(() -> service.get(sheet.getId(), stranger.getId()))
                .isInstanceOf(CharacterSheetNotFoundException.class);
        assertThatThrownBy(() -> service.update(sheet.getId(), updateReq(), stranger.getId()))
                .isInstanceOf(CharacterSheetNotFoundException.class);
        assertThatThrownBy(() -> service.applyGmAdjustment(sheet.getId(), gm, stranger.getId()))
                .isInstanceOf(CharacterSheetNotFoundException.class);
    }

    @Test
    @DisplayName("ficha de NPC: dono edita mesmo com status COMPLETED")
    void npcSheet_ownerUpdate_ignoresStatus() {
        CharacterSheetResponse r = service.update(sheet.getId(), updateReq(), owner.getId());
        assertThat(r.name()).isEqualTo("Aldric II");
        verify(sheetRepository).save(sheet);
    }

    @Test
    @DisplayName("ficha de NPC: ADD_TRAIT aceita traço de campanha à qual o NPC está associado; outra campanha -> 404")
    void npcSheet_addCampaignTrait() {
        TraitDefinition own = TraitDefinition.builder().id(UUID.randomUUID()).campaignId(campaign.getId())
                .name("Olho de Águia").rarity(TraitRarity.COMMON).category(TraitCategory.values()[0]).version(1).build();
        TraitDefinition foreign = TraitDefinition.builder().id(UUID.randomUUID()).campaignId(UUID.randomUUID())
                .name("Estranho").rarity(TraitRarity.COMMON).category(TraitCategory.values()[0]).version(1).build();
        when(catalogService.findTrait(own.getId())).thenReturn(Optional.of(own));
        when(catalogService.findTrait(foreign.getId())).thenReturn(Optional.of(foreign));
        when(campaignNpcRepository.existsByCampaignIdAndNpcId(campaign.getId(), npc.getId())).thenReturn(true);

        service.applyGmAdjustment(sheet.getId(), new GmAdjustmentRequest(GmAction.ADD_TRAIT, null, null,
                "criação", null, null, own.getId()), owner.getId());
        assertThat(sheet.getTraits()).extracting(CharacterTrait::getTraitId).containsExactly(own.getId());

        assertThatThrownBy(() -> service.applyGmAdjustment(sheet.getId(), new GmAdjustmentRequest(
                GmAction.ADD_TRAIT, null, null, "criação", null, null, foreign.getId()), owner.getId()))
                .isInstanceOf(com.tatakai.manager.exception.SheetEntryNotFoundException.class);
    }

    @Test
    @DisplayName("ficha de NPC: submit e approve -> InvalidCharacterSheetException")
    void npcSheet_submitApprove_invalid() {
        assertThatThrownBy(() -> service.submit(sheet.getId(), owner.getId()))
                .isInstanceOf(InvalidCharacterSheetException.class);
        assertThatThrownBy(() -> service.approve(sheet.getId(),
                new com.tatakai.manager.dto.request.ApproveCharacterSheetRequest(true, null), owner.getId()))
                .isInstanceOf(InvalidCharacterSheetException.class);
    }

    // ------------------------------------------------------------------ getNpcSheetInCampaign

    @Test
    @DisplayName("getNpcSheetInCampaign: Mestre da campanha vê todas as seções")
    void inCampaign_master_seesAll() {
        linkCampaignNpc();
        member(owner, Role.MASTER);
        CharacterSheetResponse r = service.getNpcSheetInCampaign(campaign.getId(), npc.getId(), owner.getId());
        assertThat(r.attributes()).isNotNull();
        assertThat(r.resources()).isNotNull();
        assertThat(r.injuries()).isNotNull();
        assertThat(r.inventory()).isNotNull();
    }

    @Test
    @DisplayName("getNpcSheetInCampaign: jogador com NPC oculto -> NpcNotFoundException")
    void inCampaign_player_hiddenNpc() {
        cn.setVisible(false);
        linkCampaignNpc();
        member(playerUser, Role.PLAYER);
        assertThatThrownBy(() -> service.getNpcSheetInCampaign(campaign.getId(), npc.getId(), playerUser.getId()))
                .isInstanceOf(NpcNotFoundException.class);
    }

    @Test
    @DisplayName("getNpcSheetInCampaign: jogador, NPC visível e sem linhas de visibilidade -> 4 seções nulas")
    void inCampaign_player_defaultAllHidden() {
        linkCampaignNpc();
        member(playerUser, Role.PLAYER);
        when(npcVisibilityRepository.findByCampaignNpcId(cn.getId())).thenReturn(List.of());

        CharacterSheetResponse r = service.getNpcSheetInCampaign(campaign.getId(), npc.getId(), playerUser.getId());

        assertThat(r.attributes()).isNull();
        assertThat(r.resources()).isNull();
        assertThat(r.injuries()).isNull();
        assertThat(r.inventory()).isNull();
    }

    @Test
    @DisplayName("getNpcSheetInCampaign: ATTRIBUTES liberado -> só atributos aparecem")
    void inCampaign_player_attributesVisible() {
        linkCampaignNpc();
        member(playerUser, Role.PLAYER);
        when(npcVisibilityRepository.findByCampaignNpcId(cn.getId())).thenReturn(List.of(
                NpcSheetVisibility.builder().campaignNpc(cn).section(SheetSection.ATTRIBUTES).visible(true).build()));

        CharacterSheetResponse r = service.getNpcSheetInCampaign(campaign.getId(), npc.getId(), playerUser.getId());

        assertThat(r.attributes()).isNotNull();
        assertThat(r.resources()).isNull();
        assertThat(r.injuries()).isNull();
        assertThat(r.inventory()).isNull();
    }

    @Test
    @DisplayName("getNpcSheetInCampaign: NPC sem ficha -> CharacterSheetNotFoundException")
    void inCampaign_noSheet() {
        linkCampaignNpc();
        member(owner, Role.MASTER);
        when(sheetRepository.findByNpcId(npc.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getNpcSheetInCampaign(campaign.getId(), npc.getId(), owner.getId()))
                .isInstanceOf(CharacterSheetNotFoundException.class);
    }

    @Test
    @DisplayName("getNpcSheetInCampaign: não-membro -> AccessDeniedException")
    void inCampaign_nonMember() {
        linkCampaignNpc();
        assertThatThrownBy(() -> service.getNpcSheetInCampaign(campaign.getId(), npc.getId(), stranger.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------ visibilidade

    @Test
    @DisplayName("listNpcSheetVisibility: só o Mestre; retorna as 4 seções, padrão false")
    void listVisibility() {
        linkCampaignNpc();
        member(owner, Role.MASTER);
        member(playerUser, Role.PLAYER);
        when(npcVisibilityRepository.findByCampaignNpcId(cn.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.listNpcSheetVisibility(campaign.getId(), npc.getId(), playerUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
        List<NpcSectionVisibilityResponse> r = service.listNpcSheetVisibility(campaign.getId(), npc.getId(), owner.getId());

        assertThat(r).extracting(NpcSectionVisibilityResponse::section).containsExactlyInAnyOrder(
                SheetSection.ATTRIBUTES, SheetSection.RESOURCES, SheetSection.INJURIES, SheetSection.INVENTORY);
        assertThat(r).noneMatch(NpcSectionVisibilityResponse::visible);
    }

    @Test
    @DisplayName("setNpcSheetVisibility: Mestre grava (upsert) e publica SHEET_UPDATED")
    void setVisibility_upsertsAndPublishes() {
        linkCampaignNpc();
        member(owner, Role.MASTER);
        when(npcVisibilityRepository.findByCampaignNpcIdAndSection(cn.getId(), SheetSection.RESOURCES))
                .thenReturn(Optional.empty());

        NpcSectionVisibilityResponse r = service.setNpcSheetVisibility(campaign.getId(), npc.getId(),
                new SetNpcSectionVisibilityRequest(SheetSection.RESOURCES, true), owner.getId());

        assertThat(r.section()).isEqualTo(SheetSection.RESOURCES);
        assertThat(r.visible()).isTrue();
        ArgumentCaptor<NpcSheetVisibility> row = ArgumentCaptor.forClass(NpcSheetVisibility.class);
        verify(npcVisibilityRepository).save(row.capture());
        assertThat(row.getValue().isVisible()).isTrue();
        assertThat(row.getValue().getSection()).isEqualTo(SheetSection.RESOURCES);
        ArgumentCaptor<SheetUpdateMessage> ev = ArgumentCaptor.forClass(SheetUpdateMessage.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().event()).isEqualTo(SheetUpdateMessage.SheetEvent.SHEET_UPDATED);
        assertThat(ev.getValue().campaignId()).isEqualTo(campaign.getId());
    }

    @Test
    @DisplayName("setNpcSheetVisibility: jogador -> 403; seção inválida (LOGS) -> InvalidCharacterSheetException")
    void setVisibility_guards() {
        linkCampaignNpc();
        member(owner, Role.MASTER);
        member(playerUser, Role.PLAYER);

        assertThatThrownBy(() -> service.setNpcSheetVisibility(campaign.getId(), npc.getId(),
                new SetNpcSectionVisibilityRequest(SheetSection.RESOURCES, true), playerUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.setNpcSheetVisibility(campaign.getId(), npc.getId(),
                new SetNpcSectionVisibilityRequest(SheetSection.LOGS, true), owner.getId()))
                .isInstanceOf(InvalidCharacterSheetException.class);
        verify(npcVisibilityRepository, never()).save(any());
    }
}
