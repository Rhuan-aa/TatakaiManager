package com.tatakai.manager.progression;

import com.tatakai.manager.dto.request.FightRequest;
import com.tatakai.manager.dto.request.InstinctChoiceRequest;
import com.tatakai.manager.dto.request.OvercomeRequest;
import com.tatakai.manager.dto.request.ReviewArtRequest;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.InvalidCharacterSheetException;
import com.tatakai.manager.repository.*;
import com.tatakai.manager.service.CatalogService;
import com.tatakai.manager.service.ProgressionService;
import com.tatakai.manager.service.SheetActivityLogService;
import com.tatakai.manager.service.rules.SheetRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProgressionService — F4b lutas, PS e Instintos")
class ProgressionServiceTest {

    @Mock private CharacterSheetRepository sheetRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private CharacterArtRepository characterArtRepository;
    @Mock private FightRecordRepository fightRepository;
    @Mock private OvercomeDistributionRepository overcomeRepository;
    @Mock private CharacterInstinctRepository instinctRepository;
    @Mock private TimeSkipRepository timeSkipRepository;
    @Mock private RivalRepository rivalRepository;
    @Mock private CatalogService catalogService;
    @Mock private SheetActivityLogService activityLog;
    @Mock private ApplicationEventPublisher events;

    private ProgressionService service;
    private final List<OvercomeDistribution> distributions = new ArrayList<>();
    private final List<CharacterInstinct> instincts = new ArrayList<>();
    private Campaign campaign;
    private User master;
    private User player;
    private CharacterSheet sheet;

    @BeforeEach
    void setUp() {
        service = new ProgressionService(sheetRepository, memberRepository, characterArtRepository, fightRepository,
                overcomeRepository, instinctRepository, timeSkipRepository, rivalRepository, catalogService, activityLog, events);
        campaign = Campaign.builder().id(UUID.randomUUID()).build();
        master = user("Mestre", Role.MASTER);
        player = user("Ana", Role.PLAYER);
        CampaignMember pm = memberRepository.findByCampaignIdAndUserId(campaign.getId(), player.getId()).orElseThrow();
        sheet = CharacterSheet.builder().id(UUID.randomUUID()).campaignMember(pm).tal(10)
                .str(4).dex(3).con(10).intl(10).cha(10).men(10).hpMax(20).spMax(10).currentHp(20).currentSp(10)
                .bodyType(BodyType.MESOMORPH).injuries(new ArrayList<>()).build();
        lenient().when(sheetRepository.findById(sheet.getId())).thenReturn(Optional.of(sheet));
        lenient().when(sheetRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(catalogService.milestonesFor(campaign.getId())).thenReturn(List.of(
                FightMilestone.builder().name("Beira do Abismo").build(), FightMilestone.builder().name("Em menor número").build()));
        lenient().when(overcomeRepository.save(any())).thenAnswer(i -> {
            OvercomeDistribution d = i.getArgument(0);
            if (d.getId() == null) { d.setId(UUID.randomUUID()); distributions.add(d); }
            return d;
        });
        lenient().when(overcomeRepository.findById(any())).thenAnswer(i ->
                distributions.stream().filter(d -> d.getId().equals(i.getArgument(0))).findFirst());
        lenient().when(overcomeRepository.existsBySheetIdAndStatus(any(), any())).thenAnswer(i ->
                distributions.stream().anyMatch(d -> d.getStatus() == i.getArgument(1)));
        lenient().when(instinctRepository.save(any())).thenAnswer(i -> {
            CharacterInstinct c = i.getArgument(0);
            instincts.add(c);
            return c;
        });
        lenient().when(instinctRepository.findBySheetIdOrderByCreatedAt(any())).thenAnswer(i -> List.copyOf(instincts));
    }

    private User user(String name, Role role) {
        User u = User.builder().id(UUID.randomUUID()).name(name).build();
        CampaignMember m = CampaignMember.builder().id(UUID.randomUUID()).campaign(campaign).user(u).role(role).build();
        lenient().when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), u.getId())).thenReturn(Optional.of(m));
        return u;
    }

    private void fight(FightClassification c, List<String> milestones, Map<UUID, Integer> artEl) {
        service.recordFight(campaign.getId(), new FightRequest(null,
                List.of(new FightRequest.Participant(sheet.getId(), c, milestones, artEl, null))), master.getId());
    }

    @Test
    @DisplayName("Mestre registra luta Real: EL +2, PS = 2 + Marcos + TAL/2 pendentes e que cruzam Barreira")
    void recordRealFight() {
        fight(FightClassification.REAL, List.of("Beira do Abismo", "Beira do Abismo"), null);

        int ps = 2 + 1 + SheetRules.modQ(10) / 2;
        assertThat(sheet.getFightExperience()).isEqualTo(2);
        assertThat(sheet.getPendingOvercomePoints()).isEqualTo(ps);
        assertThat(sheet.getBarrierOvercomePoints()).isEqualTo(ps);
        assertThatThrownBy(() -> service.recordFight(campaign.getId(), new FightRequest(null, List.of(
                new FightRequest.Participant(sheet.getId(), FightClassification.REAL, null, null, null))), player.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> fight(FightClassification.REAL, List.of("Inventado"), null))
                .isInstanceOf(InvalidCharacterSheetException.class);
    }

    @Test
    @DisplayName("Fácil só 1×/dia; sparring soma ao teto acumulado e não dá PS")
    void easyAndSparring() {
        when(fightRepository.existsBySheetIdAndClassificationAndTimeSkipIdIsNullAndFightDate(any(), any(), any()))
                .thenReturn(true);
        fight(FightClassification.EASY, null, null);
        assertThat(sheet.getFightExperience()).isZero();

        fight(FightClassification.SPARRING, null, null);
        assertThat(sheet.getFightExperience()).isEqualTo(1);
        assertThat(sheet.getSparringExperience()).isEqualTo(1);
        assertThat(sheet.getPendingOvercomePoints()).isEqualTo(1); // só o PS da Fácil; sparring não dá PS
    }

    @Test
    @DisplayName("EL da luta vira pontos de graduação nas artes escolhidas, cortado no EL ganho")
    void artEl() {
        CharacterArt ca = CharacterArt.builder().id(UUID.randomUUID()).sheet(sheet).status(CharacterArtStatus.ACTIVE)
                .graduation(Graduation.BEGINNER).build();
        when(characterArtRepository.findById(ca.getId())).thenReturn(Optional.of(ca));

        fight(FightClassification.LETHAL, null, Map.of(ca.getId(), 3));
        assertThat(ca.getGraduationPoints()).isEqualTo(3);
        fight(FightClassification.REAL, null, Map.of(ca.getId(), 5));
        assertThat(ca.getGraduationPoints()).isEqualTo(5); // cortado no EL da luta (2)
    }

    @Test
    @DisplayName("jogador pede distribuição; Mestre aprova: +1 atributo, +3 HP máx; uma pendente por vez")
    void overcomeFlow() {
        sheet.setPendingOvercomePoints(3);
        var r = service.requestOvercome(sheet.getId(), new OvercomeRequest(Map.of("STR", 2, "HP", 1)), player.getId());
        assertThat(sheet.getStr()).isEqualTo(4);
        assertThatThrownBy(() -> service.requestOvercome(sheet.getId(), new OvercomeRequest(Map.of("STR", 1)),
                player.getId())).isInstanceOf(InvalidCharacterSheetException.class);
        assertThatThrownBy(() -> service.reviewOvercome(sheet.getId(), distributions.get(0).getId(),
                new ReviewArtRequest(true, null), player.getId())).isInstanceOf(AccessDeniedException.class);

        service.reviewOvercome(sheet.getId(), distributions.get(0).getId(), new ReviewArtRequest(true, null),
                master.getId());

        assertThat(sheet.getStr()).isEqualTo(6);
        assertThat(sheet.getHpMax()).isEqualTo(23);
        assertThat(sheet.getCurrentHp()).isEqualTo(23);
        assertThat(sheet.getPendingOvercomePoints()).isZero();
        assertThat(distributions.get(0).getStatus()).isEqualTo(OvercomeStatus.APPROVED);
    }

    @Test
    @DisplayName("cruzar Barreira (3→4) exige PS de luta Real/Letal")
    void barrier() {
        sheet.setPendingOvercomePoints(2);
        assertThatThrownBy(() -> service.requestOvercome(sheet.getId(), new OvercomeRequest(Map.of("DEX", 1)),
                player.getId())).isInstanceOf(InvalidCharacterSheetException.class).hasMessageContaining("Barreira");

        sheet.setBarrierOvercomePoints(1);
        service.requestOvercome(sheet.getId(), new OvercomeRequest(Map.of("DEX", 1)), player.getId());
        service.reviewOvercome(sheet.getId(), distributions.get(0).getId(), new ReviewArtRequest(true, null),
                master.getId());
        assertThat(sheet.getDex()).isEqualTo(4);
        assertThat(sheet.getBarrierOvercomePoints()).isZero();
        assertThat(sheet.getPendingOvercomePoints()).isEqualTo(1);
    }

    @Test
    @DisplayName("Instinto: 1 vaga por patamar e patente ≤ a atual")
    void instincts() {
        Instinct aquecido = Instinct.builder().id(UUID.randomUUID()).name("Aquecido").rank(KeikenRank.SEASONED).build();
        Instinct lenda = Instinct.builder().id(UUID.randomUUID()).name("Lenda").rank(KeikenRank.LEGEND).build();
        lenient().when(catalogService.instinctsFor(campaign.getId())).thenReturn(List.of(aquecido, lenda));

        assertThatThrownBy(() -> service.chooseInstinct(sheet.getId(), new InstinctChoiceRequest(aquecido.getId()),
                player.getId())).hasMessageContaining("vaga");
        sheet.setFightExperience(15);
        assertThatThrownBy(() -> service.chooseInstinct(sheet.getId(), new InstinctChoiceRequest(lenda.getId()),
                player.getId())).hasMessageContaining("patente");

        var r = service.chooseInstinct(sheet.getId(), new InstinctChoiceRequest(aquecido.getId()), player.getId());
        assertThat(r.instincts()).extracting(i -> i.name()).containsExactly("Aquecido");
        assertThatThrownBy(() -> service.chooseInstinct(sheet.getId(), new InstinctChoiceRequest(aquecido.getId()),
                player.getId())).hasMessageContaining("já possui");
    }

    @Test
    @DisplayName("F4c — Rival no Fechar luta: cria, +1 luta e +1 Leitura até 3")
    void rival() {
        List<Rival> rivals = new ArrayList<>();
        when(rivalRepository.findBySheetIdAndName(any(), any())).thenAnswer(i ->
                rivals.stream().filter(r -> r.getName().equals(i.getArgument(1))).findFirst());
        when(rivalRepository.save(any())).thenAnswer(i -> {
            Rival r = i.getArgument(0);
            if (!rivals.contains(r)) rivals.add(r);
            return r;
        });
        for (int k = 0; k < 4; k++) {
            service.recordFight(campaign.getId(), new FightRequest(null, List.of(new FightRequest.Participant(
                    sheet.getId(), FightClassification.REAL, null, null, new FightRequest.RivalRef(" Kenta ", null)))),
                    master.getId());
        }
        assertThat(rivals).hasSize(1);
        assertThat(rivals.get(0).getName()).isEqualTo("Kenta");
        assertThat(rivals.get(0).getFights()).isEqualTo(4);
        assertThat(rivals.get(0).getReadBonus()).isEqualTo(3);
    }

    @Test
    @DisplayName("F4b — Marcos: Elo Fraco só em Real/Letal; Zebra exige 2 outros de Desvantagem; Assinatura dá +1 à arte")
    void milestoneRules() {
        when(catalogService.milestonesFor(campaign.getId())).thenReturn(List.of(
                FightMilestone.builder().name("Elo Fraco").category(MilestoneCategory.DISADVANTAGE).seriousOnly(true).build(),
                FightMilestone.builder().name("Força Bruta").category(MilestoneCategory.DISADVANTAGE).build(),
                FightMilestone.builder().name("Zebra").category(MilestoneCategory.DISADVANTAGE).minOtherDisadvantage(2).build(),
                FightMilestone.builder().name("Assinatura").category(MilestoneCategory.PRESSURE).grantsArtPoint(true).build()));
        assertThatThrownBy(() -> fight(FightClassification.EASY, List.of("Elo Fraco"), null))
                .hasMessageContaining("Real ou Letal");
        assertThatThrownBy(() -> fight(FightClassification.REAL, List.of("Zebra", "Força Bruta"), null))
                .hasMessageContaining("2 outros");
        fight(FightClassification.REAL, List.of("Zebra", "Força Bruta", "Elo Fraco"), null);

        assertThatThrownBy(() -> fight(FightClassification.REAL, List.of("Assinatura"), null))
                .hasMessageContaining("Assinatura");
        CharacterArt ca = CharacterArt.builder().id(UUID.randomUUID()).sheet(sheet).status(CharacterArtStatus.ACTIVE)
                .graduation(Graduation.BEGINNER).build();
        when(characterArtRepository.findById(ca.getId())).thenReturn(Optional.of(ca));
        fight(FightClassification.REAL, List.of("Assinatura"), Map.of(ca.getId(), 2));
        assertThat(ca.getGraduationPoints()).isEqualTo(3); // 2 de EL + 1 da Assinatura
    }
}
