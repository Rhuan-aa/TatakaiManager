package com.tatakai.manager.catalog;

import com.tatakai.manager.dto.request.MartialArtPayload;
import com.tatakai.manager.dto.request.ReviewArtRequest;
import com.tatakai.manager.dto.response.MartialArtResponse;
import com.tatakai.manager.dto.response.MartialArtSummaryResponse;
import com.tatakai.manager.entity.ArtCategory;
import com.tatakai.manager.entity.ArtStatus;
import com.tatakai.manager.entity.ArtTechnique;
import com.tatakai.manager.entity.AssemblyItem;
import com.tatakai.manager.entity.AssemblyItemKind;
import com.tatakai.manager.entity.AssemblyVariation;
import com.tatakai.manager.entity.Graduation;
import com.tatakai.manager.entity.MartialArt;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.entity.TechniqueCore;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.InvalidMartialArtException;
import com.tatakai.manager.exception.SheetEntryNotFoundException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.ClanRepository;
import com.tatakai.manager.repository.MartialArtRepository;
import com.tatakai.manager.repository.InstinctRepository;
import com.tatakai.manager.repository.FightMilestoneRepository;
import com.tatakai.manager.repository.TraitDefinitionRepository;
import com.tatakai.manager.repository.UserRepository;
import com.tatakai.manager.service.CatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogService — F3a Criação Marcial")
class MartialArtWorkflowTest {

    @Mock private TraitDefinitionRepository traitRepository;
    @Mock private ClanRepository clanRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private MartialArtRepository artRepository;
    @Mock private InstinctRepository instinctRepository;
    @Mock private FightMilestoneRepository milestoneRepository;

    private CatalogService service;
    private final UUID cid = UUID.randomUUID();
    private final UUID master = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CatalogService(traitRepository, clanRepository, memberRepository, userRepository, "", artRepository, instinctRepository, milestoneRepository);
        lenient().when(memberRepository.existsByCampaignIdAndUserId(any(), any())).thenReturn(true);
        lenient().when(memberRepository.existsByCampaignIdAndUserIdAndRole(cid, master, Role.MASTER)).thenReturn(true);
        lenient().when(artRepository.save(any(MartialArt.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static ArtTechnique technique(int... costs) {
        List<AssemblyItem> items = new ArrayList<>();
        for (int c : costs) {
            AssemblyItem i = new AssemblyItem();
            i.setLabel("Item " + c);
            i.setCost(c);
            i.setKind(c < 0 ? AssemblyItemKind.LIMITATION : AssemblyItemKind.MODULE);
            items.add(i);
        }
        ArtTechnique t = new ArtTechnique();
        t.setName("Soco");
        t.setGraduation(Graduation.BEGINNER);
        t.setVariations(new ArrayList<>(List.of(new AssemblyVariation(null, TechniqueCore.STRIKE, items))));
        return t;
    }

    private static MartialArtPayload payload(ArtCategory category, ArtTechnique... techniques) {
        return new MartialArtPayload("Punho do Vento", category, null, "Trocação", List.of(), List.of(), null, "d",
                List.of(), List.of(), List.of(), new ArrayList<>(List.of(techniques)), null);
    }

    private MartialArt stored(MartialArtResponse r) {
        MartialArt a = MartialArt.builder().id(r.id()).campaignId(r.campaignId()).name(r.name()).category(r.category())
                .status(r.status()).authorId(r.authorId()).techniques(new ArrayList<>(r.techniques())).build();
        lenient().when(artRepository.findById(a.getId())).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    @DisplayName("jogador cria Arte Criada em rascunho (categoria forçada CREATED); Mestre cria aprovada")
    void create_roles() {
        MartialArtResponse p = service.createArt(cid, payload(ArtCategory.CLASSIC, technique(3)), player);
        assertThat(p.status()).isEqualTo(ArtStatus.DRAFT);
        assertThat(p.category()).isEqualTo(ArtCategory.CREATED);
        assertThat(p.authorId()).isEqualTo(player);
        assertThat(p.techniqueChecks()).hasSize(1);

        MartialArtResponse m = service.createArt(cid, payload(ArtCategory.CLASSIC, technique(3)), master);
        assertThat(m.status()).isEqualTo(ArtStatus.APPROVED);
        assertThat(m.category()).isEqualTo(ArtCategory.CLASSIC);
    }

    @Test
    @DisplayName("enviar com técnica acima do teto -> 400; dentro do teto -> PENDING_APPROVAL")
    void submit_validates() {
        MartialArt bad = stored(service.createArt(cid, payload(ArtCategory.CREATED, technique(3, 3, 1)), player));
        assertThatThrownBy(() -> service.submitArt(bad.getId(), player)).isInstanceOf(InvalidMartialArtException.class);

        MartialArt ok = stored(service.createArt(cid, payload(ArtCategory.CREATED, technique(3, 2)), player));
        assertThat(service.submitArt(ok.getId(), player).status()).isEqualTo(ArtStatus.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("só o autor edita o próprio rascunho; outro jogador -> 403; enviado não é mais editável pelo autor")
    void update_permissions() {
        MartialArt a = stored(service.createArt(cid, payload(ArtCategory.CREATED, technique(3)), player));
        assertThat(service.updateArt(a.getId(), payload(ArtCategory.LOST, technique(4)), player).category())
                .isEqualTo(ArtCategory.CREATED);
        assertThatThrownBy(() -> service.updateArt(a.getId(), payload(ArtCategory.CREATED), other))
                .isInstanceOf(AccessDeniedException.class);

        service.submitArt(a.getId(), player);
        assertThatThrownBy(() -> service.updateArt(a.getId(), payload(ArtCategory.CREATED), player))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Mestre aprova ou devolve com motivo; jogador não revisa")
    void review() {
        MartialArt a = stored(service.createArt(cid, payload(ArtCategory.CREATED, technique(3)), player));
        service.submitArt(a.getId(), player);
        assertThatThrownBy(() -> service.reviewArt(a.getId(), new ReviewArtRequest(true, null), player))
                .isInstanceOf(AccessDeniedException.class);

        MartialArtResponse back = service.reviewArt(a.getId(), new ReviewArtRequest(false, "reduza o dano"), master);
        assertThat(back.status()).isEqualTo(ArtStatus.DRAFT);
        assertThat(back.reviewNote()).isEqualTo("reduza o dano");

        service.submitArt(a.getId(), player);
        assertThat(service.reviewArt(a.getId(), new ReviewArtRequest(true, null), master).status())
                .isEqualTo(ArtStatus.APPROVED);
    }

    @Test
    @DisplayName("rascunho só é visível ao autor; pendente também ao Mestre; outros -> 404 e fora da lista")
    void visibility() {
        MartialArt a = stored(service.createArt(cid, payload(ArtCategory.CREATED, technique(3)), player));
        when(artRepository.findByCampaignId(cid)).thenReturn(List.of(a));

        assertThat(service.getArt(a.getId(), player).id()).isEqualTo(a.getId());
        assertThatThrownBy(() -> service.getArt(a.getId(), master)).isInstanceOf(SheetEntryNotFoundException.class);
        assertThat(service.listArts(cid, other)).extracting(MartialArtSummaryResponse::id).doesNotContain(a.getId());

        service.submitArt(a.getId(), player);
        assertThat(service.getArt(a.getId(), master).status()).isEqualTo(ArtStatus.PENDING_APPROVAL);
        assertThatThrownBy(() -> service.getArt(a.getId(), other)).isInstanceOf(SheetEntryNotFoundException.class);
    }
}
