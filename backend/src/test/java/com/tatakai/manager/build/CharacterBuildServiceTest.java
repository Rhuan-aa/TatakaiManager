package com.tatakai.manager.build;

import com.tatakai.manager.dto.request.AttributeAssignmentRequest;
import com.tatakai.manager.dto.request.BuildBackRequest;
import com.tatakai.manager.dto.request.ClanChoiceRequest;
import com.tatakai.manager.dto.request.FreePointsRequest;
import com.tatakai.manager.dto.request.ReopenBuildRequest;
import com.tatakai.manager.dto.request.ResolveTraitSlotRequest;
import com.tatakai.manager.dto.request.StartBuildRequest;
import com.tatakai.manager.dto.request.TraitChoiceRequest;
import com.tatakai.manager.dto.response.CharacterBuildResponse;
import com.tatakai.manager.dto.response.SheetUpdateMessage;
import com.tatakai.manager.entity.ActorRole;
import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.BodyType;
import com.tatakai.manager.entity.BuildGains;
import com.tatakai.manager.entity.BuildSettings;
import com.tatakai.manager.entity.BuildState;
import com.tatakai.manager.entity.BuildStatus;
import com.tatakai.manager.entity.BuildStep;
import com.tatakai.manager.entity.Campaign;
import com.tatakai.manager.entity.CampaignMember;
import com.tatakai.manager.entity.CharacterBuild;
import com.tatakai.manager.entity.CharacterSheet;
import com.tatakai.manager.entity.CharacterTrait;
import com.tatakai.manager.entity.Clan;
import com.tatakai.manager.entity.Dominance;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.entity.Roll;
import com.tatakai.manager.entity.TraitAcquisition;
import com.tatakai.manager.entity.TraitCategory;
import com.tatakai.manager.entity.TraitDefinition;
import com.tatakai.manager.entity.TraitRarity;
import com.tatakai.manager.entity.TraitSlot;
import com.tatakai.manager.entity.TraitSlotStatus;
import com.tatakai.manager.entity.User;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.CharacterSheetAlreadyExistsException;
import com.tatakai.manager.exception.InvalidBuildStepException;
import com.tatakai.manager.exception.SheetLockedException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CharacterBuildRepository;
import com.tatakai.manager.repository.CharacterSheetRepository;
import com.tatakai.manager.service.CampaignRuleService;
import com.tatakai.manager.service.CatalogService;
import com.tatakai.manager.service.CharacterBuildService;
import com.tatakai.manager.service.CharacterSheetService;
import com.tatakai.manager.service.RollService;
import com.tatakai.manager.service.SheetActivityLogService;
import com.tatakai.manager.service.rules.SheetRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CharacterBuildService — Sprint 10 — F2 Construção Inicial")
class CharacterBuildServiceTest {

    @Mock private CharacterBuildRepository buildRepository;
    @Mock private CharacterSheetRepository sheetRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private CampaignRuleService ruleService;
    @Mock private CatalogService catalog;
    @Mock private RollService rollService;
    @Mock private SheetActivityLogService activityLog;
    @Mock private ApplicationEventPublisher events;
    @Mock private CharacterSheetService sheetService;

    private CharacterBuildService service;

    private Campaign campaign;
    private User masterUser;
    private User playerUser;
    private CampaignMember masterMember;
    private CampaignMember playerMember;
    private CharacterSheet sheet;
    private CharacterBuild build;

    private static final BuildSettings CLASSIC = new BuildSettings("CLASSIC", null, false, false, true);

    @BeforeEach
    void setUp() {
        service = new CharacterBuildService(buildRepository, sheetRepository, memberRepository, ruleService,
                catalog, rollService, activityLog, events, sheetService);
        campaign = Campaign.builder().id(UUID.randomUUID()).name("Mesa").build();
        masterUser = User.builder().id(UUID.randomUUID()).name("Mestre").build();
        playerUser = User.builder().id(UUID.randomUUID()).name("Ana").build();
        masterMember = CampaignMember.builder().id(UUID.randomUUID()).campaign(campaign).user(masterUser)
                .role(Role.MASTER).build();
        playerMember = CampaignMember.builder().id(UUID.randomUUID()).campaign(campaign).user(playerUser)
                .role(Role.PLAYER).build();
        lenient().when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), masterUser.getId()))
                .thenReturn(Optional.of(masterMember));
        lenient().when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), playerUser.getId()))
                .thenReturn(Optional.of(playerMember));

        sheet = CharacterSheet.builder().id(UUID.randomUUID()).campaignMember(playerMember).name("Kaito")
                .buildStatus(BuildStatus.IN_BUILD).bodyType(BodyType.MESOMORPH).dominance(Dominance.RIGHT)
                .heightM(1.7).weightKg(60).build();
        lenient().when(sheetRepository.findById(sheet.getId())).thenReturn(Optional.of(sheet));
        lenient().when(sheetRepository.save(any(CharacterSheet.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(buildRepository.save(any(CharacterBuild.class))).thenAnswer(i -> i.getArgument(0));
        build = newBuild(BuildStep.IDENTITY, CLASSIC);
    }

    private CharacterBuild newBuild(BuildStep step, BuildSettings settings) {
        CharacterBuild b = CharacterBuild.builder().sheetId(sheet.getId()).sheet(sheet).step(step)
                .settings(settings).state(new BuildState()).build();
        lenient().when(buildRepository.findById(sheet.getId())).thenReturn(Optional.of(b));
        return b;
    }

    private BuildState st() {
        return build.getState();
    }

    private static Roll roll(int total, String dice) {
        return Roll.builder().total(total).diceResults(dice).build();
    }

    private void die(String expr, int... totals) {
        Roll[] rolls = new Roll[totals.length];
        for (int i = 0; i < totals.length; i++) rolls[i] = roll(totals[i], "[" + totals[i] + "]");
        var stub = when(rollService.rollInternal(any(), eq(expr), anyString(), any(), any()));
        stub.thenReturn(rolls[0], java.util.Arrays.copyOfRange(rolls, 1, rolls.length));
    }

    private TraitDefinition trait(TraitRarity r, int number, BuildGains gains) {
        TraitDefinition t = TraitDefinition.builder().id(UUID.randomUUID()).name("T-" + r + number).rarity(r)
                .category(TraitCategory.PHYSICAL).rollNumber(number).version(2).buildGains(gains).build();
        lenient().when(catalog.findTrait(t.getId())).thenReturn(Optional.of(t));
        return t;
    }

    private Clan clan(int number, BuildGains gains) {
        Clan c = Clan.builder().id(UUID.randomUUID()).name("Cla" + number).rollNumber(number).version(3)
                .buildGains(gains).build();
        lenient().when(catalog.findClan(c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    private TraitSlot slot(int index, TraitRarity r, TraitSlotStatus status, UUID traitId) {
        TraitSlot s = new TraitSlot();
        s.setIndex(index);
        s.setRarity(r);
        s.setStatus(status);
        s.setTraitId(traitId);
        return s;
    }

    private Map<Attribute, Integer> identity() {
        Map<Attribute, Integer> m = new EnumMap<>(Attribute.class);
        for (Attribute a : Attribute.values()) m.put(a, a.ordinal());
        return m;
    }

    // ------------------------------------------------------------------ início

    @Test
    @DisplayName("start: jogador cria ficha IN_BUILD no passo IDENTITY e publica SHEET_CREATED")
    void start_ok() {
        when(sheetRepository.existsByCampaignMemberId(playerMember.getId())).thenReturn(false);
        when(ruleService.buildSettings(campaign.getId())).thenReturn(CLASSIC);

        CharacterBuildResponse r = service.start(campaign.getId(), new StartBuildRequest("Kaito"), playerUser.getId());

        assertThat(r.step()).isEqualTo(BuildStep.IDENTITY);
        assertThat(r.buildStatus()).isEqualTo(BuildStatus.IN_BUILD);
        assertThat(r.settings()).isEqualTo(CLASSIC);
        assertThat(r.freePointsAvailable()).isEqualTo(6);
        verify(activityLog).record(any(CharacterSheet.class), eq(playerUser.getId()), eq(ActorRole.PLAYER), any(),
                eq("START_BUILD"), any(), any(), any(), any(), any());
        ArgumentCaptor<SheetUpdateMessage> cap = ArgumentCaptor.forClass(SheetUpdateMessage.class);
        verify(events).publishEvent(cap.capture());
        assertThat(cap.getValue().event()).isEqualTo(SheetUpdateMessage.SheetEvent.SHEET_CREATED);
    }

    @Test
    @DisplayName("start: Mestre não tem ficha (403) e segunda ficha é rejeitada")
    void start_rules() {
        assertThatThrownBy(() -> service.start(campaign.getId(), new StartBuildRequest("X"), masterUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
        when(sheetRepository.existsByCampaignMemberId(playerMember.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.start(campaign.getId(), new StartBuildRequest("X"), playerUser.getId()))
                .isInstanceOf(CharacterSheetAlreadyExistsException.class);
        verify(buildRepository, never()).save(any());
    }

    @Test
    @DisplayName("passos fora de ordem lançam InvalidBuildStepException")
    void stepGuards() {
        // passo atual: IDENTITY
        assertThatThrownBy(() -> service.rollTraits(sheet.getId(), playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);
        assertThatThrownBy(() -> service.rollBloodTest(sheet.getId(), playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);
        assertThatThrownBy(() -> service.rollAttributes(sheet.getId(), playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);
        assertThatThrownBy(() -> service.setFreePoints(sheet.getId(), new FreePointsRequest(Map.of()),
                playerUser.getId())).isInstanceOf(InvalidBuildStepException.class);
        assertThatThrownBy(() -> service.confirmStep(sheet.getId(), BuildStep.TRAITS, playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);
        assertThatThrownBy(() -> service.submit(sheet.getId(), playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);
    }

    @Test
    @DisplayName("somente o dono muda a construção; ficha fora de IN_BUILD é SheetLockedException")
    void ownerAndLocked() {
        assertThatThrownBy(() -> service.rollTraits(sheet.getId(), masterUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
        sheet.setBuildStatus(BuildStatus.PENDING_APPROVAL);
        assertThatThrownBy(() -> service.confirmStep(sheet.getId(), BuildStep.IDENTITY, playerUser.getId()))
                .isInstanceOf(SheetLockedException.class);
    }

    // ------------------------------------------------------------------ traços

    @Test
    @DisplayName("RN-TRAIT-01 CLASSIC: rollTraits usa rollInternal e resolve pelo catálogo; Lendário 20 e traço ausente vão ao Mestre")
    void rollTraits_classic() {
        build.setStep(BuildStep.TRAITS);
        TraitDefinition common5 = trait(TraitRarity.COMMON, 5, null);
        when(catalog.traitsForCampaign(campaign.getId())).thenReturn(List.of(common5));
        die("1d4", 3);
        die("1d100", 10, 95, 60);
        die("1d20", 5, 20, 9);

        CharacterBuildResponse r = service.rollTraits(sheet.getId(), playerUser.getId());

        assertThat(r.traitCountRoll()).isEqualTo(3);
        List<TraitSlot> slots = r.traitSlots();
        assertThat(slots.get(0).getRarity()).isEqualTo(TraitRarity.COMMON);
        assertThat(slots.get(0).getStatus()).isEqualTo(TraitSlotStatus.ROLLED);
        assertThat(slots.get(0).getTraitId()).isEqualTo(common5.getId());
        assertThat(slots.get(1).getRarity()).isEqualTo(TraitRarity.LEGENDARY);
        assertThat(slots.get(1).getStatus()).isEqualTo(TraitSlotStatus.PENDING_GM);
        assertThat(slots.get(1).getTraitId()).isNull();
        assertThat(slots.get(2).getRarity()).isEqualTo(TraitRarity.RARE);
        assertThat(slots.get(2).getStatus()).isEqualTo(TraitSlotStatus.PENDING_GM); // sem traço no catálogo
        verify(rollService, times(3)).rollInternal(any(), eq("1d100"), anyString(), eq(playerUser.getId()),
                eq(ActorRole.PLAYER));

        // já rolado: só o Mestre rerrola
        assertThatThrownBy(() -> service.rollTraits(sheet.getId(), playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);
    }

    @Test
    @DisplayName("PRODIGY promove Comum a Raro")
    void rollTraits_prodigy() {
        build = newBuild(BuildStep.TRAITS, new BuildSettings("PRODIGY", null, false, false, true));
        when(catalog.traitsForCampaign(campaign.getId())).thenReturn(List.of());
        die("1d2", 1);
        die("1d100", 5);
        die("1d20", 3);

        CharacterBuildResponse r = service.rollTraits(sheet.getId(), playerUser.getId());

        assertThat(r.traitCountRoll()).isEqualTo(2); // 1d2 + 1
        assertThat(r.traitSlots().get(0).getRarity()).isEqualTo(TraitRarity.RARE);
    }

    @Test
    @DisplayName("resolveTraitSlot: só o Mestre; reposição manual resolve o slot")
    void resolveTraitSlot() {
        build.setStep(BuildStep.TRAITS);
        st().setTraitSlots(new ArrayList<>(List.of(slot(0, TraitRarity.LEGENDARY, TraitSlotStatus.PENDING_GM, null))));
        TraitDefinition x = trait(TraitRarity.LEGENDARY, 20, null);
        when(catalog.traitsForCampaign(campaign.getId())).thenReturn(List.of(x));

        assertThatThrownBy(() -> service.resolveTraitSlot(sheet.getId(), 0,
                new ResolveTraitSlotRequest(x.getId(), null, "ok"), playerUser.getId()))
                .isInstanceOf(AccessDeniedException.class);

        service.resolveTraitSlot(sheet.getId(), 0, new ResolveTraitSlotRequest(x.getId(), null, "ok"),
                masterUser.getId());

        TraitSlot s = st().getTraitSlots().get(0);
        assertThat(s.getStatus()).isEqualTo(TraitSlotStatus.RESOLVED);
        assertThat(s.getTraitId()).isEqualTo(x.getId());
    }

    @Test
    @DisplayName("resolveTraitSlot: rerrolagem depende de gmRerolls")
    void resolveTraitSlot_reroll() {
        build.setStep(BuildStep.TRAITS);
        st().setTraitSlots(new ArrayList<>(List.of(slot(0, TraitRarity.LEGENDARY, TraitSlotStatus.PENDING_GM, null))));
        build.setSettings(new BuildSettings("CLASSIC", null, false, false, false));

        assertThatThrownBy(() -> service.resolveTraitSlot(sheet.getId(), 0,
                new ResolveTraitSlotRequest(null, true, "de novo"), masterUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);

        build.setSettings(new BuildSettings("CLASSIC", null, false, false, true));
        TraitDefinition l3 = trait(TraitRarity.LEGENDARY, 3, null);
        when(catalog.traitsForCampaign(campaign.getId())).thenReturn(List.of(l3));
        die("1d20", 3);

        service.resolveTraitSlot(sheet.getId(), 0, new ResolveTraitSlotRequest(null, true, "de novo"),
                masterUser.getId());

        assertThat(st().getTraitSlots().get(0).getTraitId()).isEqualTo(l3.getId());
        assertThat(st().getTraitSlots().get(0).getStatus()).isEqualTo(TraitSlotStatus.ROLLED);
        verify(rollService).rollInternal(any(), eq("1d20"), anyString(), eq(masterUser.getId()), eq(ActorRole.MASTER));
    }

    @Test
    @DisplayName("BALANCED_CHOICE: exatamente 1 Comum, 1 Raro e 1 Épico")
    void chooseTraits_balanced() {
        build = newBuild(BuildStep.TRAITS, new BuildSettings("BALANCED_CHOICE", null, false, false, true));
        TraitDefinition c = trait(TraitRarity.COMMON, 1, null);
        TraitDefinition c2 = trait(TraitRarity.COMMON, 2, null);
        TraitDefinition r = trait(TraitRarity.RARE, 1, null);
        TraitDefinition e = trait(TraitRarity.EPIC, 1, null);
        when(catalog.traitsForCampaign(campaign.getId())).thenReturn(List.of(c, c2, r, e));

        assertThatThrownBy(() -> service.chooseTraits(sheet.getId(),
                new TraitChoiceRequest(List.of(c.getId(), c2.getId(), r.getId())), playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);
        assertThatThrownBy(() -> service.chooseTraits(sheet.getId(),
                new TraitChoiceRequest(List.of(c.getId(), r.getId())), playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);
        assertThatThrownBy(() -> service.rollTraits(sheet.getId(), playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);

        CharacterBuildResponse out = service.chooseTraits(sheet.getId(),
                new TraitChoiceRequest(List.of(c.getId(), r.getId(), e.getId())), playerUser.getId());

        assertThat(out.traitSlots()).hasSize(3).allMatch(s -> s.getStatus() == TraitSlotStatus.RESOLVED);
    }

    // ------------------------------------------------------------------ sangue e clãs

    @Test
    @DisplayName("Teste de Sangue: CLASSIC sem Épico+ usa limiar 15; senão 18; 20 dá 2")
    void bloodTest() {
        build.setStep(BuildStep.BLOOD_TEST);
        die("1d20", 15);
        CharacterBuildResponse r = service.rollBloodTest(sheet.getId(), playerUser.getId());
        assertThat(r.bloodTestRoll()).isEqualTo(15);
        assertThat(r.clanSlots()).isEqualTo(1);
        assertThatThrownBy(() -> service.rollBloodTest(sheet.getId(), playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);

        // com Épico: limiar normal
        build = newBuild(BuildStep.BLOOD_TEST, CLASSIC);
        TraitDefinition epic = trait(TraitRarity.EPIC, 1, null);
        st().setTraitSlots(new ArrayList<>(List.of(slot(0, TraitRarity.EPIC, TraitSlotStatus.ROLLED, epic.getId()))));
        die("1d20", 15);
        assertThat(service.rollBloodTest(sheet.getId(), playerUser.getId()).clanSlots()).isZero();

        build = newBuild(BuildStep.BLOOD_TEST, CLASSIC);
        die("1d20", 20);
        assertThat(service.rollBloodTest(sheet.getId(), playerUser.getId()).clanSlots()).isEqualTo(2);
    }

    @Test
    @DisplayName("chooseClans: quantidade deve igualar clanSlots e clãs devem existir na campanha")
    void chooseClans() {
        build.setStep(BuildStep.BLOOD_TEST);
        st().setBloodTestRoll(19);
        st().setClanSlots(1);
        Clan a = clan(1, null);
        Clan b = clan(2, null);
        when(catalog.clansForCampaign(campaign.getId())).thenReturn(List.of(a, b));

        assertThatThrownBy(() -> service.chooseClans(sheet.getId(),
                new ClanChoiceRequest(List.of(a.getId(), b.getId())), playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);
        assertThatThrownBy(() -> service.chooseClans(sheet.getId(),
                new ClanChoiceRequest(List.of()), playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);
        assertThatThrownBy(() -> service.chooseClans(sheet.getId(),
                new ClanChoiceRequest(List.of(UUID.randomUUID())), playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);

        service.chooseClans(sheet.getId(), new ClanChoiceRequest(List.of(b.getId())), playerUser.getId());

        assertThat(st().getClanIds()).containsExactly(b.getId());
    }

    // ------------------------------------------------------------------ atributos

    @Test
    @DisplayName("rollAttributes: 7d12 normal e 7d20 em ordem com atributos aleatórios (que pula FREE_POINTS)")
    void rollAttributes() {
        build.setStep(BuildStep.ATTRIBUTES);
        when(rollService.rollInternal(any(), eq("7d12"), anyString(), any(), any()))
                .thenReturn(roll(30, "[1,2,3,4,5,6,9]"));

        CharacterBuildResponse r = service.rollAttributes(sheet.getId(), playerUser.getId());

        assertThat(r.attributeDice()).containsExactly(1, 2, 3, 4, 5, 6, 9);
        assertThatThrownBy(() -> service.rollAttributes(sheet.getId(), playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);

        build = newBuild(BuildStep.ATTRIBUTES, new BuildSettings("CLASSIC", null, false, true, true));
        when(rollService.rollInternal(any(), eq("7d20"), anyString(), any(), any()))
                .thenReturn(roll(70, "[10, 11, 12, 13, 14, 15, 16]"));
        CharacterBuildResponse r2 = service.rollAttributes(sheet.getId(), playerUser.getId());
        assertThat(r2.attributeDice()).containsExactly(10, 11, 12, 13, 14, 15, 16);
        assertThat(r2.layers().get(Attribute.STR).base()).isEqualTo(10);
        assertThat(r2.layers().get(Attribute.TAL).base()).isEqualTo(16);
        assertThat(r2.freePointsAvailable()).isZero();

        assertThatThrownBy(() -> service.assignAttributes(sheet.getId(),
                new AttributeAssignmentRequest(identity()), playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);

        CharacterBuildResponse r3 = service.confirmStep(sheet.getId(), BuildStep.ATTRIBUTES, playerUser.getId());
        assertThat(r3.step()).isEqualTo(BuildStep.REVIEW);
        assertThat(r3.freePointsConfirmed()).isTrue();
        assertThat(r3.attributesConfirmed()).isTrue();
    }

    @Test
    @DisplayName("assignAttributes: precisa ser permutação dos 7 dados")
    void assignAttributes() {
        build.setStep(BuildStep.ATTRIBUTES);
        st().setAttributeDice(new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7)));
        Map<Attribute, Integer> dup = identity();
        dup.put(Attribute.TAL, 0);
        Map<Attribute, Integer> partial = identity();
        partial.remove(Attribute.TAL);
        Map<Attribute, Integer> outOfRange = identity();
        outOfRange.put(Attribute.TAL, 7);

        for (Map<Attribute, Integer> bad : List.of(dup, partial, outOfRange)) {
            assertThatThrownBy(() -> service.assignAttributes(sheet.getId(),
                    new AttributeAssignmentRequest(bad), playerUser.getId()))
                    .isInstanceOf(InvalidBuildStepException.class);
        }

        CharacterBuildResponse r = service.assignAttributes(sheet.getId(),
                new AttributeAssignmentRequest(identity()), playerUser.getId());

        assertThat(r.layers().get(Attribute.CON).base()).isEqualTo(3);
    }

    @Test
    @DisplayName("assignAttributes sem rolar antes é rejeitado")
    void assignAttributes_beforeRoll() {
        build.setStep(BuildStep.ATTRIBUTES);
        assertThatThrownBy(() -> service.assignAttributes(sheet.getId(),
                new AttributeAssignmentRequest(identity()), playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);
    }

    // ------------------------------------------------------------------ pontos livres

    private void toFreePointsStep(int firstDie) {
        build.setStep(BuildStep.FREE_POINTS);
        st().setAttributeDice(new ArrayList<>(List.of(firstDie, 5, 5, 5, 5, 5, 5)));
        st().setAssignment(new EnumMap<>(identity()));
        st().setAttributesConfirmed(true);
    }

    @Test
    @DisplayName("RN-ATR-06: setFreePoints rejeita além do orçamento, negativo e cruzamento de barreira")
    void setFreePoints_rejections() {
        toFreePointsStep(3);
        Map<Attribute, Integer> over = new EnumMap<>(Attribute.class);
        for (Attribute a : Attribute.values()) over.put(a, a == Attribute.STR ? 0 : 1); // 6 válidos
        over.put(Attribute.STR, 1); // STR 3 -> 4 cruza a barreira; troca para provar orçamento abaixo
        assertThatThrownBy(() -> service.setFreePoints(sheet.getId(), new FreePointsRequest(over), playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);

        Map<Attribute, Integer> sevenPoints = new EnumMap<>(Attribute.class);
        for (Attribute a : Attribute.values()) sevenPoints.put(a, a == Attribute.STR ? 0 : 1);
        sevenPoints.put(Attribute.DEX, 2); // total 7 > 6
        assertThatThrownBy(() -> service.setFreePoints(sheet.getId(), new FreePointsRequest(sevenPoints),
                playerUser.getId())).isInstanceOf(InvalidBuildStepException.class);

        assertThatThrownBy(() -> service.setFreePoints(sheet.getId(),
                new FreePointsRequest(Map.of(Attribute.DEX, -1)), playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);
    }

    @Test
    @DisplayName("setFreePoints respeita o orçamento do canhoto (3) e aceita pontos válidos")
    void setFreePoints_budgetAndOk() {
        toFreePointsStep(3);
        sheet.setDominance(Dominance.LEFT);
        assertThatThrownBy(() -> service.setFreePoints(sheet.getId(),
                new FreePointsRequest(Map.of(Attribute.DEX, 1, Attribute.CON, 1, Attribute.INT, 1, Attribute.CHA, 1)),
                playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);

        CharacterBuildResponse r = service.setFreePoints(sheet.getId(),
                new FreePointsRequest(Map.of(Attribute.DEX, 1, Attribute.CON, 1, Attribute.INT, 1)), playerUser.getId());

        assertThat(r.freePointsAvailable()).isEqualTo(3);
        assertThat(r.layers().get(Attribute.DEX).finalValue()).isEqualTo(6);
        assertThat(st().getFreePoints()).containsEntry(Attribute.DEX, 1).containsEntry(Attribute.CON, 1);
    }

    // ------------------------------------------------------------------ navegação

    @Test
    @DisplayName("back mantém dados rolados e derruba confirmações dos passos seguintes")
    void back() {
        build.setStep(BuildStep.REVIEW);
        st().setTraitsConfirmed(true);
        st().setClansConfirmed(true);
        st().setAttributesConfirmed(true);
        st().setFreePointsConfirmed(true);
        st().setAttributeDice(new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7)));
        st().setBloodTestRoll(12);

        assertThatThrownBy(() -> service.back(sheet.getId(), new BuildBackRequest(BuildStep.REVIEW),
                playerUser.getId())).isInstanceOf(InvalidBuildStepException.class);

        CharacterBuildResponse r = service.back(sheet.getId(), new BuildBackRequest(BuildStep.ATTRIBUTES),
                playerUser.getId());

        assertThat(r.step()).isEqualTo(BuildStep.ATTRIBUTES);
        assertThat(r.attributeDice()).hasSize(7);
        assertThat(r.bloodTestRoll()).isEqualTo(12);
        assertThat(r.traitsConfirmed()).isTrue();
        assertThat(r.clansConfirmed()).isTrue();
        assertThat(r.attributesConfirmed()).isFalse();
        assertThat(r.freePointsConfirmed()).isFalse();
    }

    // ------------------------------------------------------------------ envio

    @Test
    @DisplayName("RN-TRAIT-02 / RN-REC-01: submit grava atributos, máximos, limitadores, snapshots e vai a PENDING_APPROVAL")
    void submit_ok() {
        build.setStep(BuildStep.REVIEW);
        BuildGains tg = BuildGains.builder().attributeFlat(Map.of(Attribute.STR, 2)).maxHp(3)
                .limiters(Map.of("STR", 5)).build();
        BuildGains cg = BuildGains.builder().attributeFlat(Map.of(Attribute.CON, 2)).maxSp(1).build();
        TraitDefinition t = trait(TraitRarity.COMMON, 1, tg);
        Clan cl = clan(4, cg);
        st().setTraitSlots(new ArrayList<>(List.of(slot(0, TraitRarity.COMMON, TraitSlotStatus.ROLLED, t.getId()))));
        st().setClanSlots(1);
        st().setClanIds(new ArrayList<>(List.of(cl.getId())));
        st().setAttributeDice(new ArrayList<>(List.of(10, 10, 10, 10, 10, 10, 10)));
        st().setAssignment(new EnumMap<>(identity()));
        st().setFreePoints(new EnumMap<>(Map.of(Attribute.DEX, 1)));
        st().setTraitsConfirmed(true);
        st().setClansConfirmed(true);
        st().setAttributesConfirmed(true);
        st().setFreePointsConfirmed(true);
        when(sheetService.toResponseFor(sheet, playerUser.getId())).thenReturn(null);

        service.submit(sheet.getId(), playerUser.getId());

        assertThat(sheet.getStr()).isEqualTo(12);
        assertThat(sheet.getDex()).isEqualTo(11);
        assertThat(sheet.getCon()).isEqualTo(12);
        assertThat(sheet.getHpMax()).isEqualTo(SheetRules.initialHpMax(12) + 3);
        assertThat(sheet.getSpMax()).isEqualTo(SheetRules.initialSpMax(12) + 1);
        assertThat(sheet.getKpMax()).isEqualTo(SheetRules.initialKpMax(12));
        assertThat(sheet.getCurrentHp()).isEqualTo(sheet.getHpMax());
        assertThat(sheet.getLimiterAdjustments()).containsEntry("STR", 5);
        assertThat(sheet.getTraits()).hasSize(1);
        CharacterTrait ct = sheet.getTraits().get(0);
        assertThat(ct.getAcquiredIn()).isEqualTo(TraitAcquisition.INITIAL_BUILD);
        assertThat(ct.isGainsApplied()).isTrue();
        assertThat(ct.getTraitVersion()).isEqualTo(2);
        assertThat(ct.getBuildGains()).isSameAs(tg);
        assertThat(sheet.getClans()).hasSize(1);
        assertThat(sheet.getClans().get(0).getClanVersion()).isEqualTo(3);
        assertThat(sheet.getClans().get(0).getName()).isEqualTo("Cla4");
        assertThat(sheet.getBuildStatus()).isEqualTo(BuildStatus.PENDING_APPROVAL);
        verify(sheetService).toResponseFor(sheet, playerUser.getId());
        verify(activityLog).record(eq(sheet), eq(playerUser.getId()), eq(ActorRole.PLAYER), any(),
                eq("SUBMIT_BUILD"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("submit exige todos os passos confirmados")
    void submit_unconfirmed() {
        build.setStep(BuildStep.REVIEW);
        st().setTraitsConfirmed(true);
        assertThatThrownBy(() -> service.submit(sheet.getId(), playerUser.getId()))
                .isInstanceOf(InvalidBuildStepException.class);
        assertThat(sheet.getBuildStatus()).isEqualTo(BuildStatus.IN_BUILD);
    }

    @Test
    @DisplayName("submit/confirm com ficha fora de IN_BUILD é SheetLockedException")
    void submit_locked() {
        build.setStep(BuildStep.REVIEW);
        sheet.setBuildStatus(BuildStatus.PENDING_APPROVAL);
        assertThatThrownBy(() -> service.submit(sheet.getId(), playerUser.getId()))
                .isInstanceOf(SheetLockedException.class);
    }

    @Test
    @DisplayName("reopen: só o Mestre, só ficha COMPLETED; volta a IN_BUILD no passo REVIEW")
    void reopen() {
        sheet.setBuildStatus(BuildStatus.COMPLETED);
        ReopenBuildRequest req = new ReopenBuildRequest("ajuste");

        assertThatThrownBy(() -> service.reopen(sheet.getId(), req, playerUser.getId()))
                .isInstanceOf(AccessDeniedException.class);

        CharacterBuildResponse r = service.reopen(sheet.getId(), req, masterUser.getId());
        assertThat(r.buildStatus()).isEqualTo(BuildStatus.IN_BUILD);
        assertThat(r.step()).isEqualTo(BuildStep.REVIEW);

        assertThatThrownBy(() -> service.reopen(sheet.getId(), req, masterUser.getId()))
                .isInstanceOf(SheetLockedException.class);
    }
}
