package com.tatakai.manager.progression;

import com.tatakai.manager.dto.request.ForgeStartRequest;
import com.tatakai.manager.dto.request.NotebookDraftRequest;
import com.tatakai.manager.dto.request.ReviewArtRequest;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.InvalidCharacterSheetException;
import com.tatakai.manager.repository.*;
import com.tatakai.manager.service.MartialNotebookService;
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
@DisplayName("MartialNotebookService — F4c Caderno Marcial e Forja")
class MartialNotebookServiceTest {

    @Mock private CharacterSheetRepository sheetRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private CharacterArtRepository characterArtRepository;
    @Mock private CharacterTechniqueRepository techniqueRepository;
    @Mock private NotebookDraftRepository draftRepository;
    @Mock private ForgeRepository forgeRepository;
    @Mock private SheetActivityLogService activityLog;
    @Mock private ApplicationEventPublisher events;

    private MartialNotebookService service;
    private final List<NotebookDraft> drafts = new ArrayList<>();
    private final List<Forge> forges = new ArrayList<>();
    private final List<CharacterTechnique> techniques = new ArrayList<>();
    private Campaign campaign;
    private User master;
    private User player;
    private CharacterSheet sheet;

    @BeforeEach
    void setUp() {
        service = new MartialNotebookService(sheetRepository, memberRepository, characterArtRepository,
                techniqueRepository, draftRepository, forgeRepository, activityLog, events);
        campaign = Campaign.builder().id(UUID.randomUUID()).build();
        master = user("Mestre", Role.MASTER);
        player = user("Ana", Role.PLAYER);
        CampaignMember pm = memberRepository.findByCampaignIdAndUserId(campaign.getId(), player.getId()).orElseThrow();
        sheet = CharacterSheet.builder().id(UUID.randomUUID()).campaignMember(pm).tal(10).build();
        lenient().when(sheetRepository.findById(sheet.getId())).thenReturn(Optional.of(sheet));
        lenient().when(draftRepository.save(any())).thenAnswer(i -> {
            NotebookDraft d = i.getArgument(0);
            if (d.getId() == null) { d.setId(UUID.randomUUID()); drafts.add(d); }
            return d;
        });
        lenient().when(draftRepository.findById(any())).thenAnswer(i ->
                drafts.stream().filter(d -> d.getId().equals(i.getArgument(0))).findFirst());
        lenient().when(draftRepository.findBySheetIdOrderByCreatedAt(any())).thenAnswer(i -> List.copyOf(drafts));
        lenient().doAnswer(i -> drafts.remove(i.getArgument(0))).when(draftRepository).delete(any());
        lenient().when(forgeRepository.save(any())).thenAnswer(i -> {
            Forge f = i.getArgument(0);
            forges.add(f);
            return f;
        });
        lenient().when(forgeRepository.findBySheetIdAndCompletedAtIsNull(any())).thenAnswer(i ->
                forges.stream().filter(f -> f.getCompletedAt() == null).findFirst());
        lenient().when(techniqueRepository.save(any())).thenAnswer(i -> {
            techniques.add(i.getArgument(0));
            return i.getArgument(0);
        });
    }

    private User user(String name, Role role) {
        User u = User.builder().id(UUID.randomUUID()).name(name).build();
        CampaignMember m = CampaignMember.builder().id(UUID.randomUUID()).campaign(campaign).user(u).role(role).build();
        lenient().when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), u.getId())).thenReturn(Optional.of(m));
        return u;
    }

    /** Técnica Genérica com uma variação de Complexidade `cost`. */
    private static NotebookDraftRequest technique(String name, int cost) {
        ArtTechnique t = new ArtTechnique();
        t.setName(name);
        t.setGraduation(Graduation.GENERIC);
        AssemblyItem item = new AssemblyItem();
        item.setModule("Dano");
        item.setCost(cost);
        item.setKind(AssemblyItemKind.MODULE);
        t.setVariations(new ArrayList<>(List.of(new AssemblyVariation(null, TechniqueCore.STRIKE,
                new ArrayList<>(List.of(item))))));
        return new NotebookDraftRequest(MoveKind.TECHNIQUE, t, null);
    }

    @Test
    @DisplayName("Caderno: máx. 3 rascunhos; envio bloqueado com issues; Mestre aprova")
    void notebookFlow() {
        var r = service.create(sheet.getId(), technique("Golpe Novo", 3), player.getId());
        UUID id = r.drafts().get(0).id();
        assertThat(r.drafts().get(0).check().finalComplexity()).isEqualTo(3);
        service.create(sheet.getId(), technique("B", 1), player.getId());
        service.create(sheet.getId(), technique("Acima do teto", 9), player.getId());
        assertThatThrownBy(() -> service.create(sheet.getId(), technique("D", 1), player.getId()))
                .hasMessageContaining("no máximo 3");
        assertThatThrownBy(() -> service.submit(sheet.getId(), drafts.get(2).getId(), player.getId()))
                .isInstanceOf(InvalidCharacterSheetException.class).hasMessageContaining("teto");

        service.submit(sheet.getId(), id, player.getId());
        assertThatThrownBy(() -> service.review(sheet.getId(), id, new ReviewArtRequest(true, null), player.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(service.review(sheet.getId(), id, new ReviewArtRequest(true, null), master.getId())
                .drafts().get(0).status()).isEqualTo(NotebookStatus.APPROVED);
    }

    @Test
    @DisplayName("Forja: só rascunho aprovado; 3 × C_final pontos; ao completar vira avulsa em Esboço e consome o rascunho")
    void forge() {
        UUID id = service.create(sheet.getId(), technique("Golpe Novo", 2), player.getId()).drafts().get(0).id();
        assertThatThrownBy(() -> service.startForge(sheet.getId(), new ForgeStartRequest(id), player.getId()))
                .hasMessageContaining("aprovado");
        drafts.get(0).setStatus(NotebookStatus.APPROVED);

        var r = service.startForge(sheet.getId(), new ForgeStartRequest(id), player.getId());
        assertThat(r.forge().requiredPoints()).isEqualTo(6);
        assertThatThrownBy(() -> service.startForge(sheet.getId(), new ForgeStartRequest(id), player.getId()))
                .hasMessageContaining("andamento");

        for (int k = 0; k < 5; k++) assertThat(service.applySession(sheet)).startsWith("Forja Golpe Novo");
        assertThat(service.applySession(sheet)).contains("concluída");
        assertThat(techniques).singleElement().satisfies(t -> {
            assertThat(t.getCharacterArt()).isNull();
            assertThat(t.getMastery()).isEqualTo(Mastery.SKETCH);
            assertThat(t.getState()).isEqualTo(TechniqueState.ACTIVE);
        });
        assertThat(drafts).isEmpty();
        assertThat(service.applySession(sheet)).isNull();
    }

    @Test
    @DisplayName("Forja exige Keiken mínima e graduação ≤ a maior do lutador")
    void forgeRequirements() {
        NotebookDraftRequest req = technique("Iniciante", 2);
        req.technique().setGraduation(Graduation.BEGINNER);
        UUID id = service.create(sheet.getId(), req, player.getId()).drafts().get(0).id();
        drafts.get(0).setStatus(NotebookStatus.APPROVED);
        assertThatThrownBy(() -> service.startForge(sheet.getId(), new ForgeStartRequest(id), player.getId()))
                .hasMessageContaining("maior graduação");

        lenient().when(characterArtRepository.findBySheetIdOrderByCreatedAt(sheet.getId())).thenReturn(List.of(
                CharacterArt.builder().graduation(Graduation.BEGINNER).status(CharacterArtStatus.ACTIVE).build()));
        assertThatThrownBy(() -> service.startForge(sheet.getId(), new ForgeStartRequest(id), player.getId()))
                .hasMessageContaining("Keiken");
        sheet.setFightExperience(5);
        assertThat(service.startForge(sheet.getId(), new ForgeStartRequest(id), player.getId()).forge()).isNotNull();
    }
}
