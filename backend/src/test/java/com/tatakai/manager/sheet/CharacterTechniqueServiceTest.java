package com.tatakai.manager.sheet;

import com.tatakai.manager.dto.request.*;
import com.tatakai.manager.dto.response.CharacterTechniqueResponse;
import com.tatakai.manager.dto.response.SheetMartialResponse;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.InvalidCharacterSheetException;
import com.tatakai.manager.repository.*;
import com.tatakai.manager.service.CharacterTechniqueService;
import com.tatakai.manager.service.SheetActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("CharacterTechniqueService — F3b artes e técnicas na ficha")
class CharacterTechniqueServiceTest {

    @Mock private CharacterSheetRepository sheetRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private CampaignNpcRepository campaignNpcRepository;
    @Mock private CharacterArtRepository characterArtRepository;
    @Mock private CharacterTechniqueRepository techniqueRepository;
    @Mock private MartialArtRepository martialArtRepository;
    @Mock private SheetActivityLogService activityLog;
    @Mock private ApplicationEventPublisher events;

    private CharacterTechniqueService service;
    private final List<CharacterArt> arts = new ArrayList<>();
    private final List<CharacterTechnique> techniques = new ArrayList<>();
    private Campaign campaign;
    private User master;
    private User player;
    private User other;
    private CharacterSheet sheet;
    private MartialArt karate;

    @BeforeEach
    void setUp() {
        service = new CharacterTechniqueService(sheetRepository, memberRepository, campaignNpcRepository,
                characterArtRepository, techniqueRepository, martialArtRepository, activityLog, events);
        campaign = Campaign.builder().id(UUID.randomUUID()).build();
        master = user("Mestre", Role.MASTER);
        player = user("Ana", Role.PLAYER);
        other = user("Bia", Role.PLAYER);
        CampaignMember pm = memberRepository.findByCampaignIdAndUserId(campaign.getId(), player.getId()).orElseThrow();
        sheet = CharacterSheet.builder().id(UUID.randomUUID()).campaignMember(pm).tal(10).build();
        lenient().when(sheetRepository.findById(sheet.getId())).thenReturn(Optional.of(sheet));

        ArtTechnique maeGeri = new ArtTechnique();
        maeGeri.setName("Mae Geri");
        maeGeri.setGraduation(Graduation.BEGINNER);
        maeGeri.setComplexity(7);
        maeGeri.setTrainingPoints(7);
        ArtTechnique shuto = new ArtTechnique();
        shuto.setName("Shuto Uke");
        shuto.setGraduation(Graduation.INTERMEDIATE);
        shuto.setTrainingPoints(6);
        karate = MartialArt.builder().id(UUID.randomUUID()).name("Karatê").category(ArtCategory.CLASSIC)
                .techniques(new ArrayList<>(List.of(maeGeri, shuto))).bases(new ArrayList<>()).build();
        lenient().when(martialArtRepository.findById(karate.getId())).thenReturn(Optional.of(karate));

        // repositórios em memória
        lenient().when(characterArtRepository.save(any())).thenAnswer(i -> {
            CharacterArt a = i.getArgument(0);
            if (a.getId() == null) { a.setId(UUID.randomUUID()); arts.add(a); }
            return a;
        });
        lenient().when(characterArtRepository.findById(any())).thenAnswer(i ->
                arts.stream().filter(a -> a.getId().equals(i.getArgument(0))).findFirst());
        lenient().when(characterArtRepository.findBySheetIdOrderByCreatedAt(any())).thenAnswer(i -> List.copyOf(arts));
        lenient().when(characterArtRepository.existsBySheetIdAndArtId(any(), any())).thenAnswer(i ->
                arts.stream().anyMatch(a -> a.getArtId().equals(i.getArgument(1))));
        lenient().doAnswer(i -> arts.remove(i.getArgument(0))).when(characterArtRepository).delete(any());
        lenient().when(techniqueRepository.save(any())).thenAnswer(i -> {
            CharacterTechnique t = i.getArgument(0);
            if (t.getId() == null) { t.setId(UUID.randomUUID()); techniques.add(t); }
            return t;
        });
        lenient().when(techniqueRepository.findById(any())).thenAnswer(i ->
                techniques.stream().filter(t -> t.getId().equals(i.getArgument(0))).findFirst());
        lenient().when(techniqueRepository.findBySheetIdOrderByCreatedAt(any())).thenAnswer(i -> List.copyOf(techniques));
        lenient().doAnswer(i -> techniques.remove(i.getArgument(0))).when(techniqueRepository).delete(any());
    }

    private User user(String name, Role role) {
        User u = User.builder().id(UUID.randomUUID()).name(name).build();
        CampaignMember m = CampaignMember.builder().id(UUID.randomUUID()).campaign(campaign).user(u).role(role).build();
        lenient().when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), u.getId())).thenReturn(Optional.of(m));
        return u;
    }

    private UUID activeArt() {
        UUID caId = service.requestArt(sheet.getId(), new CharacterArtRequest(karate.getId()), player.getId()).arts().get(0).id();
        service.reviewArt(sheet.getId(), caId, new ReviewArtRequest(true, null), master.getId());
        return caId;
    }

    private CharacterTechniqueResponse only(SheetMartialResponse r) {
        return r.techniques().get(r.techniques().size() - 1);
    }

    @Test
    @DisplayName("jogador pede a arte -> REQUESTED; Mestre aprova -> ACTIVE; outro jogador -> 403")
    void artFlow() {
        assertThatThrownBy(() -> service.requestArt(sheet.getId(), new CharacterArtRequest(karate.getId()), other.getId()))
                .isInstanceOf(AccessDeniedException.class);
        SheetMartialResponse r = service.requestArt(sheet.getId(), new CharacterArtRequest(karate.getId()), player.getId());
        assertThat(r.arts().get(0).status()).isEqualTo(CharacterArtStatus.REQUESTED);
        assertThatThrownBy(() -> service.reviewArt(sheet.getId(), r.arts().get(0).id(), new ReviewArtRequest(true, null),
                player.getId())).isInstanceOf(AccessDeniedException.class);

        assertThat(service.reviewArt(sheet.getId(), r.arts().get(0).id(), new ReviewArtRequest(true, null), master.getId())
                .arts().get(0).status()).isEqualTo(CharacterArtStatus.ACTIVE);
    }

    @Test
    @DisplayName("técnica: pede com snapshot do livro; graduação acima da arte -> 400; Mestre aprova e treina até ativar")
    void learnAndTrain() {
        UUID caId = activeArt();
        assertThatThrownBy(() -> service.learn(sheet.getId(), new LearnMoveRequest(caId, MoveKind.TECHNIQUE, "Shuto Uke"),
                player.getId())).isInstanceOf(InvalidCharacterSheetException.class);

        CharacterTechniqueResponse t = only(service.learn(sheet.getId(),
                new LearnMoveRequest(caId, MoveKind.TECHNIQUE, "Mae Geri"), player.getId()));
        assertThat(t.state()).isEqualTo(TechniqueState.REQUESTED);
        assertThat(t.trainingPoints()).isEqualTo(7);

        service.reviewTechnique(sheet.getId(), t.id(), new ReviewArtRequest(true, null), master.getId());
        assertThat(only(service.train(sheet.getId(), t.id(), new PointsRequest(4), master.getId())).state())
                .isEqualTo(TechniqueState.IN_TRAINING);
        CharacterTechniqueResponse done = only(service.train(sheet.getId(), t.id(), new PointsRequest(5), master.getId()));
        assertThat(done.state()).isEqualTo(TechniqueState.ACTIVE);
        assertThat(done.trainingProgress()).isEqualTo(7);
        assertThat(done.mastery()).isEqualTo(Mastery.STANDARD);
    }

    @Test
    @DisplayName("maestria: sobe com pontos suficientes, consome o custo e registra a bonificação; Ápice em Masterizada")
    void levelUp() {
        UUID caId = activeArt();
        UUID tid = only(service.learn(sheet.getId(), new LearnMoveRequest(caId, MoveKind.TECHNIQUE, "Mae Geri"),
                master.getId())).id();
        service.train(sheet.getId(), tid, new PointsRequest(7), master.getId());

        assertThatThrownBy(() -> service.levelUp(sheet.getId(), tid, new LevelUpRequest(MasteryBonus.DAMAGE), player.getId()))
                .isInstanceOf(InvalidCharacterSheetException.class);
        service.addMasteryPoints(sheet.getId(), tid, new PointsRequest(7), master.getId());

        assertThatThrownBy(() -> service.levelUp(sheet.getId(), tid, new LevelUpRequest(MasteryBonus.BONUS), player.getId()))
                .isInstanceOf(InvalidCharacterSheetException.class);
        service.levelUp(sheet.getId(), tid, new LevelUpRequest(MasteryBonus.DAMAGE), player.getId());
        service.addMasteryPoints(sheet.getId(), tid, new PointsRequest(10), master.getId());
        service.levelUp(sheet.getId(), tid, new LevelUpRequest(MasteryBonus.COST), player.getId());
        service.addMasteryPoints(sheet.getId(), tid, new PointsRequest(14), master.getId());
        CharacterTechniqueResponse r = only(service.levelUp(sheet.getId(), tid, new LevelUpRequest(MasteryBonus.HIT),
                player.getId()));

        assertThat(r.mastery()).isEqualTo(Mastery.MASTERED);
        assertThat(r.masteryPoints()).isZero();
        assertThat(r.bonuses()).containsExactly(MasteryBonus.DAMAGE, MasteryBonus.COST, MasteryBonus.HIT, MasteryBonus.APEX);
        assertThat(r.nextLevelCost()).isNull();
    }

    @Test
    @DisplayName("pontos de maestria: só positivos e limitados ao custo do próximo nível")
    void masteryPointsBounds() {
        UUID caId = activeArt();
        UUID tid = only(service.learn(sheet.getId(), new LearnMoveRequest(caId, MoveKind.TECHNIQUE, "Mae Geri"),
                master.getId())).id();
        service.train(sheet.getId(), tid, new PointsRequest(7), master.getId());

        assertThatThrownBy(() -> service.addMasteryPoints(sheet.getId(), tid, new PointsRequest(-3), master.getId()))
                .isInstanceOf(InvalidCharacterSheetException.class);
        assertThat(only(service.addMasteryPoints(sheet.getId(), tid, new PointsRequest(50), master.getId())).masteryPoints())
                .isEqualTo(7);
    }

    @Test
    @DisplayName("voltar o degrau de maestria remove as bonificações acima dele e zera os pontos")
    void setMasteryTrimsBonuses() {
        UUID caId = activeArt();
        UUID tid = only(service.learn(sheet.getId(), new LearnMoveRequest(caId, MoveKind.TECHNIQUE, "Mae Geri"),
                master.getId())).id();
        service.train(sheet.getId(), tid, new PointsRequest(7), master.getId());
        service.addMasteryPoints(sheet.getId(), tid, new PointsRequest(7), master.getId());
        service.levelUp(sheet.getId(), tid, new LevelUpRequest(MasteryBonus.DAMAGE), player.getId());
        service.addMasteryPoints(sheet.getId(), tid, new PointsRequest(10), master.getId());
        service.levelUp(sheet.getId(), tid, new LevelUpRequest(MasteryBonus.COST), player.getId());
        service.addMasteryPoints(sheet.getId(), tid, new PointsRequest(5), master.getId());

        CharacterTechniqueResponse r = only(service.setMastery(sheet.getId(), tid, new MasteryRequest(Mastery.IMPROVED),
                master.getId()));
        assertThat(r.bonuses()).containsExactly(MasteryBonus.DAMAGE);
        assertThat(r.masteryPoints()).isZero();
        assertThat(only(service.setMastery(sheet.getId(), tid, new MasteryRequest(Mastery.UNCORRECTED), master.getId()))
                .bonuses()).isEmpty();
    }

    @Test
    @DisplayName("espaços: no máx. 3 técnicas ativas por arte e graduação; a 4ª completa vai para guardadas")
    void slots() {
        UUID caId = activeArt();
        CharacterArt ca = arts.get(0);
        for (int i = 0; i < 3; i++) {
            techniques.add(CharacterTechnique.builder().id(UUID.randomUUID()).sheet(sheet).characterArt(ca)
                    .kind(MoveKind.TECHNIQUE).name("T" + i).graduation(Graduation.BEGINNER)
                    .state(TechniqueState.ACTIVE).mastery(Mastery.STANDARD).bonuses(new ArrayList<>()).build());
        }
        UUID tid = only(service.learn(sheet.getId(), new LearnMoveRequest(caId, MoveKind.TECHNIQUE, "Mae Geri"),
                master.getId())).id();

        assertThat(only(service.train(sheet.getId(), tid, new PointsRequest(7), master.getId())).state())
                .isEqualTo(TechniqueState.STORED);
        assertThatThrownBy(() -> service.setState(sheet.getId(), tid, new TechniqueStateRequest(TechniqueState.ACTIVE),
                player.getId())).isInstanceOf(InvalidCharacterSheetException.class);
    }

    @Test
    @DisplayName("F4b — RN-PROG-09: requisitos na resposta; graduar zera pontos e concede as bases da nova graduação")
    void graduate() {
        ArtBase kiba = new ArtBase();
        kiba.setName("Kiba Dachi");
        kiba.setGraduation(Graduation.INTERMEDIATE);
        kiba.setComplexity(4);
        kiba.setTrainingPoints(4);
        karate.getBases().add(kiba);
        UUID caId = activeArt();
        CharacterArt ca = arts.get(0);

        var art = service.get(sheet.getId(), player.getId()).arts().get(0);
        assertThat(art.graduationCost()).isEqualTo(8);
        assertThat(art.requirements()).extracting(r -> r.key())
                .containsExactly("COMPLETENESS", "SPECIALIZATION", "KEIKEN", "POINTS");
        assertThatThrownBy(() -> service.graduate(sheet.getId(), caId, new GraduateRequest(null), master.getId()))
                .isInstanceOf(InvalidCharacterSheetException.class).hasMessageContaining("COMPLETENESS");

        for (Mastery m : List.of(Mastery.IMPROVED, Mastery.IMPROVED, Mastery.SPECIALIZED)) {
            techniques.add(CharacterTechnique.builder().id(UUID.randomUUID()).sheet(sheet).characterArt(ca)
                    .kind(MoveKind.TECHNIQUE).name("T" + techniques.size()).graduation(Graduation.BEGINNER)
                    .state(TechniqueState.ACTIVE).mastery(m).bonuses(new ArrayList<>()).build());
        }
        sheet.setFightExperience(5);
        service.addGraduationPoints(sheet.getId(), caId, new PointsRequest(8), master.getId());
        assertThatThrownBy(() -> service.graduate(sheet.getId(), caId, new GraduateRequest(null), player.getId()))
                .isInstanceOf(AccessDeniedException.class);

        SheetMartialResponse r = service.graduate(sheet.getId(), caId, new GraduateRequest(null), master.getId());

        assertThat(r.arts().get(0).graduation()).isEqualTo(Graduation.INTERMEDIATE);
        assertThat(r.arts().get(0).graduationPoints()).isZero();
        assertThat(r.arts().get(0).graduationCost()).isEqualTo(12);
        assertThat(r.techniques()).anyMatch(t -> t.name().equals("Kiba Dachi") && t.state() == TechniqueState.ACTIVE);
    }

    @Test
    @DisplayName("F4b — treino de graduação do TimeSkip: +1 ponto só em arte ativa com próxima graduação")
    void graduationTraining() {
        activeArt();
        CharacterArt ca = arts.get(0);
        assertThat(service.applyGraduationTraining(ca)).isEqualTo("graduação 1");
        ca.setGraduation(Graduation.MASTER);
        assertThat(service.applyGraduationTraining(ca)).isNull();
    }

    @Test
    @DisplayName("F4c — aprovar a arte concede as bases até a graduação; o Mestre pode remover uma depois")
    void artGrantsBases() {
        ArtBase kokutsu = new ArtBase();
        kokutsu.setName("Kokutsu Dachi");
        kokutsu.setGraduation(Graduation.BEGINNER);
        kokutsu.setTrainingPoints(4);
        ArtBase kiba = new ArtBase();
        kiba.setName("Kiba Dachi");
        kiba.setGraduation(Graduation.INTERMEDIATE);
        karate.getBases().addAll(List.of(kokutsu, kiba));

        activeArt();

        assertThat(techniques).singleElement().satisfies(t -> {
            assertThat(t.getName()).isEqualTo("Kokutsu Dachi");
            assertThat(t.getState()).isEqualTo(TechniqueState.ACTIVE);
        });
        service.removeTechnique(sheet.getId(), techniques.get(0).getId(), master.getId());
        assertThat(techniques).isEmpty();
    }

    @Test
    @DisplayName("F4c — técnica emprestada: aprende pela arte que empresta, na graduação da tabela; só 1 ativa por graduação")
    void borrowed() {
        ArtTechnique jab = new ArtTechnique();
        jab.setName("Jab");
        jab.setGraduation(Graduation.BEGINNER);
        jab.setComplexity(3);
        jab.setTrainingPoints(3);
        ArtTechnique cruzado = new ArtTechnique();
        cruzado.setName("Cruzado");
        cruzado.setGraduation(Graduation.BEGINNER);
        cruzado.setTrainingPoints(3);
        MartialArt boxe = MartialArt.builder().id(UUID.randomUUID()).name("Boxe").category(ArtCategory.CLASSIC)
                .techniques(new ArrayList<>(List.of(jab, cruzado))).bases(new ArrayList<>()).build();
        lenient().when(martialArtRepository.findByCampaignIdIsNull()).thenReturn(List.of(boxe, karate));
        karate.setBorrowed(new ArrayList<>(List.of(new BorrowedTechnique("Boxe", "Jab", Graduation.BEGINNER),
                new BorrowedTechnique("Boxe", "Cruzado", Graduation.BEGINNER))));
        UUID caId = activeArt();

        service.learn(sheet.getId(), new LearnMoveRequest(caId, MoveKind.TECHNIQUE, "Jab"), master.getId());
        service.learn(sheet.getId(), new LearnMoveRequest(caId, MoveKind.TECHNIQUE, "Cruzado"), master.getId());
        assertThat(techniques).extracting(CharacterTechnique::getBorrowedFrom).containsOnly("Boxe");
        assertThat(techniques.get(0).getComplexity()).isEqualTo(3);

        service.train(sheet.getId(), techniques.get(0).getId(), new PointsRequest(3), master.getId());
        assertThat(techniques.get(0).getState()).isEqualTo(TechniqueState.ACTIVE);
        service.train(sheet.getId(), techniques.get(1).getId(), new PointsRequest(3), master.getId());
        assertThat(techniques.get(1).getState()).isEqualTo(TechniqueState.STORED); // 1 emprestada ativa por graduação
    }
}
