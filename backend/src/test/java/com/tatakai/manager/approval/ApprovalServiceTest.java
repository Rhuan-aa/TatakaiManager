package com.tatakai.manager.approval;

import com.tatakai.manager.approval.dto.response.ApprovalItemResponse;
import com.tatakai.manager.approval.service.ApprovalService;
import com.tatakai.manager.auth.entity.User;
import com.tatakai.manager.build.entity.BuildState;
import com.tatakai.manager.build.entity.BuildStatus;
import com.tatakai.manager.build.entity.CharacterBuild;
import com.tatakai.manager.build.entity.TraitSlot;
import com.tatakai.manager.build.entity.TraitSlotStatus;
import com.tatakai.manager.build.repository.CharacterBuildRepository;
import com.tatakai.manager.campaign.entity.CampaignMember;
import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.exception.CampaignNotFoundException;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.campaign.repository.CampaignRepository;
import com.tatakai.manager.catalog.entity.ArtStatus;
import com.tatakai.manager.catalog.entity.MartialArt;
import com.tatakai.manager.catalog.entity.MoveKind;
import com.tatakai.manager.catalog.repository.MartialArtRepository;
import com.tatakai.manager.log.entity.InteractionLog;
import com.tatakai.manager.log.entity.LogStatus;
import com.tatakai.manager.log.repository.InteractionLogRepository;
import com.tatakai.manager.martial.entity.CharacterArt;
import com.tatakai.manager.martial.entity.CharacterArtStatus;
import com.tatakai.manager.martial.entity.CharacterTechnique;
import com.tatakai.manager.martial.entity.NotebookDraft;
import com.tatakai.manager.martial.entity.NotebookStatus;
import com.tatakai.manager.martial.entity.TechniqueState;
import com.tatakai.manager.martial.repository.CharacterArtRepository;
import com.tatakai.manager.martial.repository.CharacterTechniqueRepository;
import com.tatakai.manager.martial.repository.NotebookDraftRepository;
import com.tatakai.manager.progression.entity.OvercomeDistribution;
import com.tatakai.manager.progression.entity.OvercomeStatus;
import com.tatakai.manager.progression.repository.OvercomeDistributionRepository;
import com.tatakai.manager.shared.exception.AccessDeniedException;
import com.tatakai.manager.sheet.entity.CharacterSheet;
import com.tatakai.manager.sheet.repository.CharacterSheetRepository;
import com.tatakai.manager.timeskip.entity.SubmissionStatus;
import com.tatakai.manager.timeskip.entity.TimeSkip;
import com.tatakai.manager.timeskip.entity.TimeSkipStatus;
import com.tatakai.manager.timeskip.entity.TimeSkipSubmission;
import com.tatakai.manager.timeskip.repository.TimeSkipRepository;
import com.tatakai.manager.timeskip.repository.TimeSkipSubmissionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalService — fila única de pendências do Mestre")
class ApprovalServiceTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private CharacterSheetRepository sheetRepository;
    @Mock private CharacterBuildRepository buildRepository;
    @Mock private CharacterArtRepository artRepository;
    @Mock private CharacterTechniqueRepository techniqueRepository;
    @Mock private NotebookDraftRepository draftRepository;
    @Mock private OvercomeDistributionRepository overcomeRepository;
    @Mock private MartialArtRepository martialArtRepository;
    @Mock private InteractionLogRepository logRepository;
    @Mock private TimeSkipRepository timeSkipRepository;
    @Mock private TimeSkipSubmissionRepository submissionRepository;

    private ApprovalService service;

    private final UUID campaignId = UUID.randomUUID();
    private final UUID masterId = UUID.randomUUID();
    private final Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
    private User player;
    private CharacterSheet sheet;

    @BeforeEach
    void setUp() {
        service = new ApprovalService(campaignRepository, memberRepository, sheetRepository, buildRepository,
                artRepository, techniqueRepository, draftRepository, overcomeRepository, martialArtRepository,
                logRepository, timeSkipRepository, submissionRepository);
        player = User.builder().id(UUID.randomUUID()).name("Jogador").build();
        sheet = CharacterSheet.builder().id(UUID.randomUUID()).name("Ficha X")
                .campaignMember(CampaignMember.builder().user(player).role(Role.PLAYER).build())
                .buildStatus(BuildStatus.COMPLETED).updatedAt(t0).build();
    }

    private void asMaster() {
        when(campaignRepository.existsById(campaignId)).thenReturn(true);
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(campaignId, masterId, Role.MASTER))
                .thenReturn(true);
    }

    private void withSheet() {
        asMaster();
        when(sheetRepository.findByCampaignMemberCampaignId(campaignId)).thenReturn(List.of(sheet));
    }

    @Test
    @DisplayName("não-Mestre recebe 403")
    void naoMestre() {
        when(campaignRepository.existsById(campaignId)).thenReturn(true);
        assertThatThrownBy(() -> service.list(campaignId, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("campanha inexistente -> 404")
    void campanhaInexistente() {
        assertThatThrownBy(() -> service.list(campaignId, masterId))
                .isInstanceOf(CampaignNotFoundException.class);
    }

    @Test
    @DisplayName("sem pendências devolve lista vazia")
    void vazio() {
        asMaster();
        assertThat(service.list(campaignId, masterId)).isEmpty();
    }

    @Test
    @DisplayName("SHEET: ficha PENDING_APPROVAL aparece; COMPLETED não")
    void sheetPendente() {
        withSheet();
        assertThat(service.list(campaignId, masterId)).isEmpty();

        sheet.setBuildStatus(BuildStatus.PENDING_APPROVAL);
        List<ApprovalItemResponse> r = service.list(campaignId, masterId);
        assertThat(r).hasSize(1);
        assertThat(r.get(0).kind()).isEqualTo("SHEET");
        assertThat(r.get(0).sheetId()).isEqualTo(sheet.getId());
        assertThat(r.get(0).title()).isEqualTo("Ficha X");
        assertThat(r.get(0).requesterName()).isEqualTo("Jogador");
        assertThat(r.get(0).userId()).isEqualTo(player.getId());
    }

    @Test
    @DisplayName("BUILD_TRAIT: só slot PENDING_GM")
    void buildTrait() {
        withSheet();
        TraitSlot pend = new TraitSlot();
        pend.setStatus(TraitSlotStatus.PENDING_GM);
        pend.setTraitName("Sorte");
        TraitSlot ok = new TraitSlot();
        ok.setStatus(TraitSlotStatus.RESOLVED);
        BuildState st = new BuildState();
        st.setTraitSlots(List.of(pend, ok));
        when(buildRepository.findById(sheet.getId()))
                .thenReturn(Optional.of(CharacterBuild.builder().state(st).updatedAt(t0).build()));

        List<ApprovalItemResponse> r = service.list(campaignId, masterId);
        assertThat(r).hasSize(1);
        assertThat(r.get(0).kind()).isEqualTo("BUILD_TRAIT");
        assertThat(r.get(0).title()).isEqualTo("Traço: Sorte");
        assertThat(r.get(0).sheetId()).isEqualTo(sheet.getId());
    }

    @Test
    @DisplayName("ART_REQUEST: só REQUESTED")
    void artRequest() {
        withSheet();
        UUID catalogArt = UUID.randomUUID();
        when(artRepository.findBySheetIdOrderByCreatedAt(sheet.getId())).thenReturn(List.of(
                CharacterArt.builder().id(UUID.randomUUID()).artId(catalogArt).artName("Karatê")
                        .status(CharacterArtStatus.REQUESTED).createdAt(t0).build(),
                CharacterArt.builder().id(UUID.randomUUID()).artName("Boxe")
                        .status(CharacterArtStatus.ACTIVE).createdAt(t0).build()));

        List<ApprovalItemResponse> r = service.list(campaignId, masterId);
        assertThat(r).hasSize(1);
        assertThat(r.get(0).kind()).isEqualTo("ART_REQUEST");
        assertThat(r.get(0).title()).contains("Karatê");
        assertThat(r.get(0).artId()).isEqualTo(catalogArt);
    }

    @Test
    @DisplayName("TECHNIQUE_REQUEST: só REQUESTED")
    void techniqueRequest() {
        withSheet();
        when(techniqueRepository.findBySheetIdOrderByCreatedAt(sheet.getId())).thenReturn(List.of(
                CharacterTechnique.builder().id(UUID.randomUUID()).name("Soco Reto")
                        .state(TechniqueState.REQUESTED).createdAt(t0).build(),
                CharacterTechnique.builder().id(UUID.randomUUID()).name("Gancho")
                        .state(TechniqueState.ACTIVE).createdAt(t0).build()));

        List<ApprovalItemResponse> r = service.list(campaignId, masterId);
        assertThat(r).hasSize(1);
        assertThat(r.get(0).kind()).isEqualTo("TECHNIQUE_REQUEST");
        assertThat(r.get(0).title()).isEqualTo("Soco Reto");
    }

    @Test
    @DisplayName("NOTEBOOK_DRAFT: só PENDING_APPROVAL")
    void notebookDraft() {
        withSheet();
        com.tatakai.manager.catalog.entity.ArtTechnique tech = new com.tatakai.manager.catalog.entity.ArtTechnique();
        tech.setName("Golpe Novo");
        when(draftRepository.findBySheetIdOrderByCreatedAt(sheet.getId())).thenReturn(List.of(
                NotebookDraft.builder().id(UUID.randomUUID()).kind(MoveKind.TECHNIQUE).technique(tech)
                        .status(NotebookStatus.PENDING_APPROVAL).createdAt(t0).build(),
                NotebookDraft.builder().id(UUID.randomUUID()).kind(MoveKind.TECHNIQUE).technique(tech)
                        .status(NotebookStatus.DRAFT).createdAt(t0).build()));

        List<ApprovalItemResponse> r = service.list(campaignId, masterId);
        assertThat(r).hasSize(1);
        assertThat(r.get(0).kind()).isEqualTo("NOTEBOOK_DRAFT");
        assertThat(r.get(0).title()).isEqualTo("Golpe Novo");
    }

    @Test
    @DisplayName("OVERCOME: só PENDING")
    void overcome() {
        withSheet();
        when(overcomeRepository.findBySheetIdOrderByCreatedAtDesc(sheet.getId())).thenReturn(List.of(
                OvercomeDistribution.builder().id(UUID.randomUUID()).status(OvercomeStatus.PENDING)
                        .createdAt(t0).build(),
                OvercomeDistribution.builder().id(UUID.randomUUID()).status(OvercomeStatus.APPROVED)
                        .createdAt(t0).build()));

        List<ApprovalItemResponse> r = service.list(campaignId, masterId);
        assertThat(r).hasSize(1);
        assertThat(r.get(0).kind()).isEqualTo("OVERCOME");
        assertThat(r.get(0).title()).isEqualTo("Distribuição de PS");
    }

    @Test
    @DisplayName("CREATED_ART: só PENDING_APPROVAL da campanha, com nome do autor")
    void createdArt() {
        asMaster();
        CampaignMember m = CampaignMember.builder().user(player).role(Role.PLAYER).build();
        when(memberRepository.findByCampaignId(campaignId)).thenReturn(List.of(m));
        MartialArt pend = MartialArt.builder().id(UUID.randomUUID()).name("Arte Nova")
                .status(ArtStatus.PENDING_APPROVAL).authorId(player.getId()).updatedAt(t0).build();
        MartialArt draft = MartialArt.builder().id(UUID.randomUUID()).name("Rascunho")
                .status(ArtStatus.DRAFT).authorId(player.getId()).build();
        when(martialArtRepository.findByCampaignId(campaignId)).thenReturn(List.of(pend, draft));

        List<ApprovalItemResponse> r = service.list(campaignId, masterId);
        assertThat(r).hasSize(1);
        assertThat(r.get(0).kind()).isEqualTo("CREATED_ART");
        assertThat(r.get(0).artId()).isEqualTo(pend.getId());
        assertThat(r.get(0).title()).isEqualTo("Arte Nova");
        assertThat(r.get(0).requesterName()).isEqualTo("Jogador");
    }

    @Test
    @DisplayName("LOG: só PENDING, título truncado em 60 caracteres")
    void log() {
        asMaster();
        InteractionLog pend = InteractionLog.builder().id(UUID.randomUUID()).author(player)
                .narrative("x".repeat(100)).status(LogStatus.PENDING).createdAt(t0).build();
        InteractionLog pub = InteractionLog.builder().id(UUID.randomUUID()).author(player)
                .narrative("ok").status(LogStatus.PUBLISHED).createdAt(t0).build();
        when(logRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId)).thenReturn(List.of(pend, pub));

        List<ApprovalItemResponse> r = service.list(campaignId, masterId);
        assertThat(r).hasSize(1);
        assertThat(r.get(0).kind()).isEqualTo("LOG");
        assertThat(r.get(0).logId()).isEqualTo(pend.getId());
        assertThat(r.get(0).title()).hasSize(60);
        assertThat(r.get(0).userId()).isEqualTo(player.getId());
    }

    @Test
    @DisplayName("TIMESKIP_SUBMISSION: só PENDING de TimeSkip ACTIVE; TimeSkip encerrado é ignorado")
    void timeSkipSubmission() {
        asMaster();
        CampaignMember m = CampaignMember.builder().user(player).role(Role.PLAYER).build();
        when(memberRepository.findByCampaignId(campaignId)).thenReturn(List.of(m));
        TimeSkip ativo = TimeSkip.builder().id(UUID.randomUUID()).name("Verão")
                .status(TimeSkipStatus.ACTIVE).build();
        TimeSkip fechado = TimeSkip.builder().id(UUID.randomUUID()).name("Velho")
                .status(TimeSkipStatus.CLOSED).build();
        when(timeSkipRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId))
                .thenReturn(List.of(ativo, fechado));
        TimeSkipSubmission sub = TimeSkipSubmission.builder().id(UUID.randomUUID()).timeSkipId(ativo.getId())
                .userId(player.getId()).status(SubmissionStatus.PENDING).submittedAt(t0).build();
        when(submissionRepository.findByTimeSkipIdInAndStatus(
                java.util.Set.of(ativo.getId()), SubmissionStatus.PENDING)).thenReturn(List.of(sub));

        List<ApprovalItemResponse> r = service.list(campaignId, masterId);
        assertThat(r).hasSize(1);
        assertThat(r.get(0).kind()).isEqualTo("TIMESKIP_SUBMISSION");
        assertThat(r.get(0).timeSkipId()).isEqualTo(ativo.getId());
        assertThat(r.get(0).title()).isEqualTo("Agendamentos de Verão");
        assertThat(r.get(0).requesterName()).isEqualTo("Jogador");
        assertThat(r.get(0).userId()).isEqualTo(player.getId());
    }

    @Test
    @DisplayName("ordena por createdAt crescente, nulo por último")
    void ordenacao() {
        asMaster();
        InteractionLog novo = InteractionLog.builder().id(UUID.randomUUID()).author(player).narrative("novo")
                .status(LogStatus.PENDING).createdAt(t0.plusSeconds(100)).build();
        InteractionLog velho = InteractionLog.builder().id(UUID.randomUUID()).author(player).narrative("velho")
                .status(LogStatus.PENDING).createdAt(t0).build();
        when(logRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId)).thenReturn(List.of(novo, velho));
        MartialArt semData = MartialArt.builder().id(UUID.randomUUID()).name("Sem data")
                .status(ArtStatus.PENDING_APPROVAL).build();
        when(martialArtRepository.findByCampaignId(campaignId)).thenReturn(List.of(semData));

        List<ApprovalItemResponse> r = service.list(campaignId, masterId);
        assertThat(r).extracting(ApprovalItemResponse::title).containsExactly("velho", "novo", "Sem data");
    }
}
