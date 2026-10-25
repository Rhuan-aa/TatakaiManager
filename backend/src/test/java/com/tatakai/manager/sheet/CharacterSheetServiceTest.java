package com.tatakai.manager.sheet;

import com.tatakai.manager.dto.request.ApproveCharacterSheetRequest;
import com.tatakai.manager.dto.request.AttributesPayload;
import com.tatakai.manager.dto.request.CreateCharacterSheetRequest;
import com.tatakai.manager.dto.request.GmAdjustmentRequest;
import com.tatakai.manager.dto.request.InjuryPayload;
import com.tatakai.manager.dto.request.ItemPayload;
import com.tatakai.manager.dto.request.UpdateCharacterSheetRequest;
import com.tatakai.manager.dto.request.UpdateResourcesRequest;
import com.tatakai.manager.dto.response.CharacterSheetResponse;
import com.tatakai.manager.dto.response.SheetUpdateMessage;
import com.tatakai.manager.entity.ActivityCategory;
import com.tatakai.manager.entity.ActorRole;
import com.tatakai.manager.entity.BodyType;
import com.tatakai.manager.entity.BuildStatus;
import com.tatakai.manager.entity.Campaign;
import com.tatakai.manager.entity.CampaignMember;
import com.tatakai.manager.entity.CharacterSheet;
import com.tatakai.manager.entity.CharacterSheetVisibility;
import com.tatakai.manager.entity.Dominance;
import com.tatakai.manager.entity.GmAction;
import com.tatakai.manager.entity.InjurySeverity;
import com.tatakai.manager.entity.InjuryType;
import com.tatakai.manager.entity.InventoryItem;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.entity.SheetSection;
import com.tatakai.manager.entity.User;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.CharacterSheetAlreadyExistsException;
import com.tatakai.manager.exception.InvalidCharacterSheetException;
import com.tatakai.manager.exception.SheetLockedException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CharacterSheetRepository;
import com.tatakai.manager.repository.CharacterSheetVisibilityRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CharacterSheetService — Sprint 9 — F1 Ficha")
class CharacterSheetServiceTest {

    @Mock private CharacterSheetRepository sheetRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private CharacterSheetVisibilityRepository visibilityRepository;
    @Mock private SheetActivityLogService activityLog;
    @Mock private ApplicationEventPublisher events;
    @Mock private com.tatakai.manager.repository.CampaignNpcRepository campaignNpcRepository;
    @Mock private com.tatakai.manager.repository.NpcSheetVisibilityRepository npcVisibilityRepository;
    @Mock private com.tatakai.manager.repository.NpcRepository npcRepository;
    @Mock private com.tatakai.manager.repository.UserRepository userRepository;
    @Mock private com.tatakai.manager.service.CampaignRuleService ruleService;
    @Mock private com.tatakai.manager.service.CatalogService catalogService;
    @Mock private com.tatakai.manager.repository.CharacterBuildRepository buildRepository;

    private CharacterSheetService service;

    private Campaign campaign;
    private User masterUser;
    private User playerUser;
    private User otherUser;
    private CampaignMember masterMember;
    private CampaignMember playerMember;
    private CampaignMember otherMember;
    private CharacterSheet sheet;

    @BeforeEach
    void setUp() {
        service = new CharacterSheetService(sheetRepository, memberRepository, visibilityRepository,
                activityLog, events, campaignNpcRepository, npcVisibilityRepository, npcRepository, userRepository,
                ruleService, catalogService, buildRepository);
        campaign = Campaign.builder().id(UUID.randomUUID()).name("Mesa").build();
        masterUser = user("Mestre");
        playerUser = user("Ana");
        otherUser = user("Bia");
        masterMember = member(masterUser, Role.MASTER);
        playerMember = member(playerUser, Role.PLAYER);
        otherMember = member(otherUser, Role.PLAYER);

        sheet = CharacterSheet.builder()
                .id(UUID.randomUUID()).campaignMember(playerMember).name("Kaito")
                .buildStatus(BuildStatus.DRAFT).bodyType(BodyType.MESOMORPH).dominance(Dominance.RIGHT)
                .heightM(1.8).weightKg(75)
                .str(10).dex(10).con(12).intl(10).cha(10).men(10).tal(10)
                .hpMax(22).spMax(11).kpMax(4).currentHp(22).currentSp(11).currentKp(4)
                .injuries(new ArrayList<>()).inventory(new ArrayList<>())
                .build();

        lenient().when(sheetRepository.findById(sheet.getId())).thenReturn(Optional.of(sheet));
        lenient().when(sheetRepository.save(any(CharacterSheet.class))).thenAnswer(i -> i.getArgument(0));
    }

    private User user(String name) {
        return User.builder().id(UUID.randomUUID()).name(name).email(name + "@rpg.com").build();
    }

    private CampaignMember member(User u, Role role) {
        CampaignMember m = CampaignMember.builder().id(UUID.randomUUID()).campaign(campaign).user(u)
                .role(role).build();
        lenient().when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), u.getId()))
                .thenReturn(Optional.of(m));
        return m;
    }

    private AttributesPayload attrs(int con) {
        return new AttributesPayload(10, 10, con, 10, 10, 10, 10);
    }

    private CreateCharacterSheetRequest createReq() {
        return new CreateCharacterSheetRequest("Kaito", BodyType.MESOMORPH, Dominance.RIGHT, 1.8, null, attrs(12));
    }

    private GmAdjustmentRequest gm(GmAction action, String field, Integer value) {
        return new GmAdjustmentRequest(action, field, value, "motivo", null, null, null);
    }

    private SheetUpdateMessage assertPublishedAndLogged(ActivityCategory category, String action) {
        ArgumentCaptor<SheetUpdateMessage> cap = ArgumentCaptor.forClass(SheetUpdateMessage.class);
        verify(events).publishEvent(cap.capture());
        SheetUpdateMessage msg = cap.getValue();
        assertThat(msg.campaignId()).isEqualTo(campaign.getId());
        assertThat(msg.category()).isEqualTo(category);
        assertThat(msg.action()).isEqualTo(action);
        return msg;
    }

    // ------------------------------------------------------------------ create

    @Test
    @DisplayName("RN-ATR-07: jogador cria ficha com máximos iniciais derivados de CON e atuais = máximos")
    void create_player_ok() {
        when(ruleService.isEnabled(campaign.getId(), com.tatakai.manager.entity.OptionalRule.FREE_CREATION)).thenReturn(true);
        when(sheetRepository.existsByCampaignMemberId(playerMember.getId())).thenReturn(false);

        CharacterSheetResponse r = service.create(campaign.getId(), createReq(), playerUser.getId());

        assertThat(r.buildStatus()).isEqualTo(BuildStatus.DRAFT);
        assertThat(r.resources().hpMax()).isEqualTo(22);
        assertThat(r.resources().spMax()).isEqualTo(11);
        assertThat(r.resources().kpMax()).isEqualTo(4);
        assertThat(r.resources().currentHp()).isEqualTo(22);
        assertThat(r.weightKg()).isGreaterThan(0);
        verify(activityLog).record(any(CharacterSheet.class), eq(playerUser.getId()), eq(ActorRole.PLAYER),
                eq(ActivityCategory.BUILD), eq("CREATE_SHEET"), any(), any(), any(), any(), any());
        SheetUpdateMessage msg = assertPublishedAndLogged(ActivityCategory.BUILD, "CREATE_SHEET");
        assertThat(msg.event()).isEqualTo(SheetUpdateMessage.SheetEvent.SHEET_CREATED);
    }

    @Test
    @DisplayName("Mestre não pode criar ficha (403)")
    void create_master_denied() {
        assertThatThrownBy(() -> service.create(campaign.getId(), createReq(), masterUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
        verify(sheetRepository, never()).save(any());
    }

    @Test
    @DisplayName("segunda ficha do mesmo jogador na campanha é rejeitada")
    void create_second_rejected() {
        when(ruleService.isEnabled(campaign.getId(), com.tatakai.manager.entity.OptionalRule.FREE_CREATION)).thenReturn(true);
        when(sheetRepository.existsByCampaignMemberId(playerMember.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.create(campaign.getId(), createReq(), playerUser.getId()))
                .isInstanceOf(CharacterSheetAlreadyExistsException.class);
    }

    @Test
    @DisplayName("não-membro não pode criar ficha (403)")
    void create_nonMember_denied() {
        UUID stranger = UUID.randomUUID();
        when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), stranger)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(campaign.getId(), createReq(), stranger))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------ update / submit / approve

    private UpdateCharacterSheetRequest updateReq() {
        return new UpdateCharacterSheetRequest("Novo", BodyType.ENDOMORPH, Dominance.LEFT, 1.7, 70.0, attrs(20));
    }

    @Test
    @DisplayName("dono edita a ficha em rascunho e máximos são recalculados")
    void update_draft_ok() {
        CharacterSheetResponse r = service.update(sheet.getId(), updateReq(), playerUser.getId());

        assertThat(r.name()).isEqualTo("Novo");
        assertThat(r.resources().hpMax()).isEqualTo(30);
        verify(activityLog).record(eq(sheet), eq(playerUser.getId()), eq(ActorRole.PLAYER),
                eq(ActivityCategory.BUILD), eq("UPDATE_SHEET"), any(), any(), any(), any(), any());
        assertPublishedAndLogged(ActivityCategory.BUILD, "UPDATE_SHEET");
    }

    @Test
    @DisplayName("edição fora de DRAFT lança SheetLockedException")
    void update_notDraft_locked() {
        sheet.setBuildStatus(BuildStatus.PENDING_APPROVAL);

        assertThatThrownBy(() -> service.update(sheet.getId(), updateReq(), playerUser.getId()))
                .isInstanceOf(SheetLockedException.class);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("quem não é dono não edita (403)")
    void update_nonOwner_denied() {
        assertThatThrownBy(() -> service.update(sheet.getId(), updateReq(), otherUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update(sheet.getId(), updateReq(), masterUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("submit move DRAFT para PENDING_APPROVAL, registra e publica")
    void submit_ok() {
        CharacterSheetResponse r = service.submit(sheet.getId(), playerUser.getId());

        assertThat(r.buildStatus()).isEqualTo(BuildStatus.PENDING_APPROVAL);
        verify(activityLog).record(eq(sheet), eq(playerUser.getId()), eq(ActorRole.PLAYER),
                eq(ActivityCategory.BUILD), eq("SUBMIT_SHEET"), eq("buildStatus"),
                eq(BuildStatus.DRAFT), eq(BuildStatus.PENDING_APPROVAL), any(), any());
        assertPublishedAndLogged(ActivityCategory.BUILD, "SUBMIT_SHEET");
    }

    @Test
    @DisplayName("submit só vale em DRAFT e só para o dono")
    void submit_rules() {
        assertThatThrownBy(() -> service.submit(sheet.getId(), otherUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
        sheet.setBuildStatus(BuildStatus.COMPLETED);
        assertThatThrownBy(() -> service.submit(sheet.getId(), playerUser.getId()))
                .isInstanceOf(SheetLockedException.class);
    }

    @Test
    @DisplayName("Mestre aprova: PENDING_APPROVAL vira COMPLETED")
    void approve_ok() {
        sheet.setBuildStatus(BuildStatus.PENDING_APPROVAL);

        CharacterSheetResponse r = service.approve(sheet.getId(),
                new ApproveCharacterSheetRequest(true, null), masterUser.getId());

        assertThat(r.buildStatus()).isEqualTo(BuildStatus.COMPLETED);
        verify(activityLog).record(eq(sheet), eq(masterUser.getId()), eq(ActorRole.MASTER),
                eq(ActivityCategory.BUILD), eq("APPROVE_SHEET"), eq("buildStatus"),
                eq(BuildStatus.PENDING_APPROVAL), eq(BuildStatus.COMPLETED), any(), any());
        assertPublishedAndLogged(ActivityCategory.BUILD, "APPROVE_SHEET");
    }

    @Test
    @DisplayName("Mestre rejeita: ficha volta para DRAFT com o motivo registrado")
    void reject_returnsToDraft() {
        sheet.setBuildStatus(BuildStatus.PENDING_APPROVAL);

        CharacterSheetResponse r = service.approve(sheet.getId(),
                new ApproveCharacterSheetRequest(false, "ajustar atributos"), masterUser.getId());

        assertThat(r.buildStatus()).isEqualTo(BuildStatus.DRAFT);
        verify(activityLog).record(eq(sheet), eq(masterUser.getId()), eq(ActorRole.MASTER),
                eq(ActivityCategory.BUILD), eq("REJECT_SHEET"), any(), any(), any(), any(),
                eq("ajustar atributos"));
    }

    @Test
    @DisplayName("aprovar exige Mestre e ficha pendente")
    void approve_rules() {
        sheet.setBuildStatus(BuildStatus.PENDING_APPROVAL);
        assertThatThrownBy(() -> service.approve(sheet.getId(),
                new ApproveCharacterSheetRequest(true, null), playerUser.getId()))
                .isInstanceOf(AccessDeniedException.class);

        sheet.setBuildStatus(BuildStatus.DRAFT);
        assertThatThrownBy(() -> service.approve(sheet.getId(),
                new ApproveCharacterSheetRequest(true, null), masterUser.getId()))
                .isInstanceOf(SheetLockedException.class);
    }

    // ------------------------------------------------------------------ get / visibilidade

    private CharacterSheetVisibility hidden(SheetSection section) {
        return CharacterSheetVisibility.builder().campaignMember(playerMember).section(section)
                .hiddenByMaster(true).build();
    }

    @Test
    @DisplayName("seção oculta vira null para outro jogador")
    void get_hiddenSection_nullForOtherPlayer() {
        when(visibilityRepository.findByCampaignMemberId(playerMember.getId()))
                .thenReturn(List.of(hidden(SheetSection.ATTRIBUTES), hidden(SheetSection.INVENTORY)));

        CharacterSheetResponse r = service.get(sheet.getId(), otherUser.getId());

        assertThat(r.attributes()).isNull();
        assertThat(r.derived()).isNull();
        assertThat(r.inventory()).isNull();
        assertThat(r.money()).isNull();
        assertThat(r.resources()).isNotNull();
        assertThat(r.injuries()).isNotNull();
    }

    @Test
    @DisplayName("Mestre e dono veem tudo mesmo com seções ocultas")
    void get_masterAndOwner_seeAll() {
        CharacterSheetResponse forMaster = service.get(sheet.getId(), masterUser.getId());
        CharacterSheetResponse forOwner = service.get(sheet.getId(), playerUser.getId());

        assertThat(forMaster.attributes()).isNotNull();
        assertThat(forMaster.derived()).isNotNull();
        assertThat(forOwner.attributes()).isNotNull();
        assertThat(forOwner.inventory()).isNotNull();
        verify(visibilityRepository, never()).findByCampaignMemberId(any());
    }

    @Test
    @DisplayName("não-membro não vê a ficha (403)")
    void get_nonMember_denied() {
        UUID stranger = UUID.randomUUID();
        when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), stranger)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(sheet.getId(), stranger)).isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------ recursos

    @Test
    @DisplayName("dono atualiza recursos e valores acima do máximo são limitados")
    void updateResources_clampsToMax() {
        sheet.setCurrentHp(5);

        CharacterSheetResponse r = service.updateResources(sheet.getId(),
                new UpdateResourcesRequest(500, 3, 999, null, null, "descanso"), playerUser.getId());

        assertThat(r.resources().currentHp()).isEqualTo(22);
        assertThat(r.resources().currentSp()).isEqualTo(3);
        assertThat(r.resources().currentKp()).isEqualTo(4);
        verify(activityLog, org.mockito.Mockito.times(3)).record(eq(sheet), eq(playerUser.getId()),
                eq(ActorRole.PLAYER), eq(ActivityCategory.RESOURCE), eq("UPDATE_RESOURCE"), any(), any(), any(),
                any(), eq("descanso"));
        assertPublishedAndLogged(ActivityCategory.RESOURCE, "UPDATE_RESOURCES");
    }

    @Test
    @DisplayName("HP atual é limitado ao máximo efetivo (com ferimentos)")
    void updateResources_clampsToEffectiveMax() {
        sheet.getInjuries().add(com.tatakai.manager.entity.PermanentInjury.builder().sheet(sheet)
                .type(InjuryType.CUTTING).severity(InjurySeverity.MINOR).hpMaxReduction(2).build());

        CharacterSheetResponse r = service.updateResources(sheet.getId(),
                new UpdateResourcesRequest(100, null, null, null, null, null), playerUser.getId());

        assertThat(r.resources().currentHp()).isEqualTo(20);
    }

    @Test
    @DisplayName("dono define o desgaste físico e mental; valores são registrados no log")
    void updateResources_setsFatigue() {
        CharacterSheetResponse r = service.updateResources(sheet.getId(),
                new UpdateResourcesRequest(null, null, null, 4, 7, null), playerUser.getId());

        assertThat(r.fatigue().physical()).isEqualTo(4);
        assertThat(r.fatigue().mental()).isEqualTo(7);
        verify(activityLog).record(eq(sheet), eq(playerUser.getId()), eq(ActorRole.PLAYER),
                eq(ActivityCategory.RESOURCE), eq("UPDATE_RESOURCE"), eq("physicalFatigue"), any(), eq(4), any(), any());
    }

    @Test
    @DisplayName("outro jogador não atualiza recursos (403); Mestre pode")
    void updateResources_access() {
        assertThatThrownBy(() -> service.updateResources(sheet.getId(),
                new UpdateResourcesRequest(1, null, null, null, null, null), otherUser.getId()))
                .isInstanceOf(AccessDeniedException.class);

        service.updateResources(sheet.getId(), new UpdateResourcesRequest(1, null, null, null, null, null),
                masterUser.getId());
        verify(activityLog).record(eq(sheet), eq(masterUser.getId()), eq(ActorRole.MASTER),
                eq(ActivityCategory.RESOURCE), eq("UPDATE_RESOURCE"), eq("HP"), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ ajustes do Mestre

    @Test
    @DisplayName("ajuste do Mestre por não-Mestre é 403 e não grava nada")
    void gm_nonMaster_denied() {
        assertThatThrownBy(() -> service.applyGmAdjustment(sheet.getId(),
                gm(GmAction.ADJUST_MONEY, null, 10), playerUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
        verify(sheetRepository, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("SET_RESOURCE limita ao máximo e registra GM_ADJUSTMENT")
    void gm_setResource_clamps() {
        CharacterSheetResponse r = service.applyGmAdjustment(sheet.getId(),
                gm(GmAction.SET_RESOURCE, "HP", 999), masterUser.getId());

        assertThat(r.resources().currentHp()).isEqualTo(22);
        verify(activityLog).record(eq(sheet), eq(masterUser.getId()), eq(ActorRole.MASTER),
                eq(ActivityCategory.GM_ADJUSTMENT), eq("SET_RESOURCE"), eq("HP"), eq(22), eq(22), any(),
                eq("motivo"));
        assertPublishedAndLogged(ActivityCategory.GM_ADJUSTMENT, "SET_RESOURCE");
    }

    @Test
    @DisplayName("SET_MAX sobe o atual somente se estava cheio")
    void gm_setMax_raisesCurrentOnlyIfFull() {
        service.applyGmAdjustment(sheet.getId(), gm(GmAction.SET_MAX, "SP", 20), masterUser.getId());
        assertThat(sheet.getSpMax()).isEqualTo(20);
        assertThat(sheet.getCurrentSp()).isEqualTo(20);

        sheet.setCurrentKp(2);
        service.applyGmAdjustment(sheet.getId(), gm(GmAction.SET_MAX, "KP", 9), masterUser.getId());
        assertThat(sheet.getKpMax()).isEqualTo(9);
        assertThat(sheet.getCurrentKp()).isEqualTo(2);
    }

    @Test
    @DisplayName("SET_MAX de HP cheio sobe o HP atual; reduzir máximo limita o atual")
    void gm_setMaxHp() {
        service.applyGmAdjustment(sheet.getId(), gm(GmAction.SET_MAX, "HP", 30), masterUser.getId());
        assertThat(sheet.getCurrentHp()).isEqualTo(30);

        service.applyGmAdjustment(sheet.getId(), gm(GmAction.SET_MAX, "HP", 10), masterUser.getId());
        assertThat(sheet.getCurrentHp()).isEqualTo(10);
    }

    @Test
    @DisplayName("ADD_INJURY reduz o HP máximo efetivo e limita o HP atual")
    void gm_addInjury_reducesEffectiveHp() {
        GmAdjustmentRequest req = new GmAdjustmentRequest(GmAction.ADD_INJURY, null, null, "golpe",
                new InjuryPayload(InjuryType.CUTTING, "braço", InjurySeverity.SEVERE, null, null), null, null);

        CharacterSheetResponse r = service.applyGmAdjustment(sheet.getId(), req, masterUser.getId());

        // 22 * 45% = 9 -> máx efetivo 13
        assertThat(sheet.getInjuries()).hasSize(1);
        assertThat(r.resources().effectiveHpMax()).isEqualTo(13);
        assertThat(sheet.getCurrentHp()).isEqualTo(13);
        assertThat(r.injuries()).hasSize(1);
        assertPublishedAndLogged(ActivityCategory.GM_ADJUSTMENT, "ADD_INJURY");
    }

    @Test
    @DisplayName("ADD_INJURY sem dados é inválido")
    void gm_addInjury_missingPayload() {
        assertThatThrownBy(() -> service.applyGmAdjustment(sheet.getId(),
                gm(GmAction.ADD_INJURY, null, null), masterUser.getId()))
                .isInstanceOf(InvalidCharacterSheetException.class);
    }

    @Test
    @DisplayName("ADJUST_FATIGUE soma e não pode ficar abaixo de zero")
    void gm_adjustFatigue() {
        service.applyGmAdjustment(sheet.getId(), gm(GmAction.ADJUST_FATIGUE, "PHYSICAL", 4), masterUser.getId());
        assertThat(sheet.getPhysicalFatigue()).isEqualTo(4);

        assertThatThrownBy(() -> service.applyGmAdjustment(sheet.getId(),
                gm(GmAction.ADJUST_FATIGUE, "PHYSICAL", -5), masterUser.getId()))
                .isInstanceOf(InvalidCharacterSheetException.class);
        assertThat(sheet.getPhysicalFatigue()).isEqualTo(4);
    }

    @Test
    @DisplayName("ADD_ITEM e REMOVE_ITEM alteram o inventário na categoria INVENTORY")
    void gm_addAndRemoveItem() {
        GmAdjustmentRequest add = new GmAdjustmentRequest(GmAction.ADD_ITEM, null, null, "loot", null,
                new ItemPayload("Poção", 2, "cura", null), null);
        service.applyGmAdjustment(sheet.getId(), add, masterUser.getId());

        assertThat(sheet.getInventory()).hasSize(1);
        assertThat(sheet.getInventory().get(0).getName()).isEqualTo("Poção");
        assertPublishedAndLogged(ActivityCategory.INVENTORY, "ADD_ITEM");

        InventoryItem item = sheet.getInventory().get(0);
        item.setId(UUID.randomUUID());
        GmAdjustmentRequest remove = new GmAdjustmentRequest(GmAction.REMOVE_ITEM, null, null, "usou", null,
                null, item.getId());
        service.applyGmAdjustment(sheet.getId(), remove, masterUser.getId());

        assertThat(sheet.getInventory()).isEmpty();
        verify(activityLog).record(eq(sheet), eq(masterUser.getId()), eq(ActorRole.MASTER),
                eq(ActivityCategory.INVENTORY), eq("REMOVE_ITEM"), any(), any(), eq(null), any(), eq("usou"));
    }

    @Test
    @DisplayName("campos obrigatórios ausentes geram InvalidCharacterSheetException")
    void gm_missingFields() {
        assertThatThrownBy(() -> service.applyGmAdjustment(sheet.getId(),
                gm(GmAction.ADJUST_MONEY, null, null), masterUser.getId()))
                .isInstanceOf(InvalidCharacterSheetException.class);
        assertThatThrownBy(() -> service.applyGmAdjustment(sheet.getId(),
                gm(GmAction.SET_RESOURCE, null, 5), masterUser.getId()))
                .isInstanceOf(InvalidCharacterSheetException.class);
        assertThatThrownBy(() -> service.applyGmAdjustment(sheet.getId(),
                gm(GmAction.SET_RESOURCE, "XYZ", 5), masterUser.getId()))
                .isInstanceOf(InvalidCharacterSheetException.class);
        assertThatThrownBy(() -> service.applyGmAdjustment(sheet.getId(),
                gm(GmAction.ADD_ITEM, null, null), masterUser.getId()))
                .isInstanceOf(InvalidCharacterSheetException.class);
        verify(events, never()).publishEvent(any(Object.class));
    }

    // ------------------------------------------------------------------ Sprint 10 — F2

    @Test
    @DisplayName("Sprint 10 — F2 Construção Inicial: create bloqueado quando FREE_CREATION está desligada")
    void create_blockedWithoutFreeCreation() {
        when(ruleService.isEnabled(campaign.getId(), com.tatakai.manager.entity.OptionalRule.FREE_CREATION)).thenReturn(false);

        assertThatThrownBy(() -> service.create(campaign.getId(), createReq(), playerUser.getId()))
                .isInstanceOf(InvalidCharacterSheetException.class);
        verify(sheetRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sprint 10 — F2 Construção Inicial: rejeição volta para IN_BUILD quando existe construção")
    void reject_returnsToInBuildWhenBuildExists() {
        sheet.setBuildStatus(BuildStatus.PENDING_APPROVAL);
        when(buildRepository.existsById(sheet.getId())).thenReturn(true);

        CharacterSheetResponse r = service.approve(sheet.getId(),
                new ApproveCharacterSheetRequest(false, "refazer"), masterUser.getId());

        assertThat(r.buildStatus()).isEqualTo(BuildStatus.IN_BUILD);
    }

    private com.tatakai.manager.entity.TraitDefinition catalogTrait(UUID campaignId) {
        return com.tatakai.manager.entity.TraitDefinition.builder().id(UUID.randomUUID()).campaignId(campaignId)
                .name("Prodígio").rarity(com.tatakai.manager.entity.TraitRarity.RARE)
                .category(com.tatakai.manager.entity.TraitCategory.values()[0]).version(3)
                .buildGains(com.tatakai.manager.entity.BuildGains.builder().maxHp(5).build()).build();
    }

    @Test
    @DisplayName("Sprint 10 — F2 Construção Inicial: RN-TRAIT-01 ADD_TRAIT grava snapshot sem aplicar ganhos")
    void gm_addTrait_snapshotWithoutGains() {
        var t = catalogTrait(null);
        when(catalogService.findTrait(t.getId())).thenReturn(Optional.of(t));

        service.applyGmAdjustment(sheet.getId(), new GmAdjustmentRequest(GmAction.ADD_TRAIT, null, null,
                "motivo", null, null, t.getId()), masterUser.getId());

        assertThat(sheet.getTraits()).hasSize(1);
        var ct = sheet.getTraits().get(0);
        assertThat(ct.getTraitId()).isEqualTo(t.getId());
        assertThat(ct.getTraitVersion()).isEqualTo(3);
        assertThat(ct.isGainsApplied()).isFalse();
        assertThat(ct.getAcquiredIn()).isEqualTo(com.tatakai.manager.entity.TraitAcquisition.CAMPAIGN);
        assertThat(sheet.getHpMax()).isEqualTo(22);
    }

    @Test
    @DisplayName("Sprint 10 — F2 Construção Inicial: ADD_TRAIT rejeita traço de outra campanha e duplicado")
    void gm_addTrait_rejections() {
        var foreign = catalogTrait(UUID.randomUUID());
        when(catalogService.findTrait(foreign.getId())).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.applyGmAdjustment(sheet.getId(), new GmAdjustmentRequest(
                GmAction.ADD_TRAIT, null, null, "motivo", null, null, foreign.getId()), masterUser.getId()))
                .isInstanceOf(com.tatakai.manager.exception.SheetEntryNotFoundException.class);

        var own = catalogTrait(campaign.getId());
        when(catalogService.findTrait(own.getId())).thenReturn(Optional.of(own));
        var req = new GmAdjustmentRequest(GmAction.ADD_TRAIT, null, null, "motivo", null, null, own.getId());
        service.applyGmAdjustment(sheet.getId(), req, masterUser.getId());
        assertThatThrownBy(() -> service.applyGmAdjustment(sheet.getId(), req, masterUser.getId()))
                .isInstanceOf(InvalidCharacterSheetException.class);
    }

    @Test
    @DisplayName("Sprint 10 — F2 Construção Inicial: REMOVE_TRAIT remove da ficha")
    void gm_removeTrait() {
        var ct = com.tatakai.manager.entity.CharacterTrait.builder().id(UUID.randomUUID()).sheet(sheet)
                .name("Prodígio").build();
        sheet.getTraits().add(ct);

        service.applyGmAdjustment(sheet.getId(), new GmAdjustmentRequest(GmAction.REMOVE_TRAIT, null, null,
                "motivo", null, null, ct.getId()), masterUser.getId());

        assertThat(sheet.getTraits()).isEmpty();
    }
}
