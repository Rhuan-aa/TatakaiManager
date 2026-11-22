package com.tatakai.manager.sheet;

import com.tatakai.manager.auth.entity.User;
import com.tatakai.manager.build.entity.BuildStatus;
import com.tatakai.manager.campaign.dto.response.CampaignUpdateMessage;
import com.tatakai.manager.campaign.entity.Campaign;
import com.tatakai.manager.campaign.entity.CampaignMember;
import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.catalog.entity.Graduation;
import com.tatakai.manager.catalog.entity.MoveKind;
import com.tatakai.manager.martial.entity.CharacterArt;
import com.tatakai.manager.martial.entity.CharacterTechnique;
import com.tatakai.manager.martial.dto.response.SheetMartialResponse;
import com.tatakai.manager.martial.repository.CharacterArtRepository;
import com.tatakai.manager.martial.repository.CharacterTechniqueRepository;
import com.tatakai.manager.martial.service.CharacterTechniqueService;
import com.tatakai.manager.catalog.repository.MartialArtRepository;
import com.tatakai.manager.npc.entity.CampaignNpc;
import com.tatakai.manager.npc.entity.Npc;
import com.tatakai.manager.npc.entity.NpcInteraction;
import com.tatakai.manager.npc.repository.CampaignNpcRepository;
import com.tatakai.manager.npc.repository.NpcRepository;
import com.tatakai.manager.shared.exception.AccessDeniedException;
import com.tatakai.manager.sheet.dto.request.UpdateNpcSheetVisibilityRequest;
import com.tatakai.manager.sheet.dto.response.CharacterSheetResponse;
import com.tatakai.manager.sheet.dto.response.NpcSectionVisibilityResponse;
import com.tatakai.manager.sheet.entity.BodyType;
import com.tatakai.manager.sheet.entity.CharacterClan;
import com.tatakai.manager.sheet.entity.CharacterSheet;
import com.tatakai.manager.sheet.entity.CharacterTrait;
import com.tatakai.manager.sheet.entity.Dominance;
import com.tatakai.manager.sheet.entity.NpcSheetVisibility;
import com.tatakai.manager.sheet.entity.SheetSection;
import com.tatakai.manager.sheet.exception.InvalidCharacterSheetException;
import com.tatakai.manager.sheet.repository.CharacterSheetRepository;
import com.tatakai.manager.sheet.repository.CharacterSheetVisibilityRepository;
import com.tatakai.manager.sheet.repository.NpcSheetVisibilityRepository;
import com.tatakai.manager.sheet.service.CharacterSheetService;
import com.tatakai.manager.sheet.service.SheetActivityLogService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Visibilidade por seção da ficha de NPC (aplicada no servidor)")
class NpcSheetVisibilityTest {

    @Mock private CharacterSheetRepository sheetRepository;
    @Mock private com.tatakai.manager.roll.service.RollService rollService;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private CharacterSheetVisibilityRepository visibilityRepository;
    @Mock private SheetActivityLogService activityLog;
    @Mock private ApplicationEventPublisher events;
    @Mock private CampaignNpcRepository campaignNpcRepository;
    @Mock private NpcSheetVisibilityRepository npcVisibilityRepository;
    @Mock private NpcRepository npcRepository;
    @Mock private com.tatakai.manager.auth.repository.UserRepository userRepository;
    @Mock private com.tatakai.manager.campaign.service.CampaignRuleService ruleService;
    @Mock private com.tatakai.manager.catalog.service.CatalogService catalogService;
    @Mock private com.tatakai.manager.build.repository.CharacterBuildRepository buildRepository;
    @Mock private com.tatakai.manager.sheet.repository.SheetImageRepository imageRepository;
    @Mock private CharacterArtRepository characterArtRepository;
    @Mock private CharacterTechniqueRepository techniqueRepository;
    @Mock private MartialArtRepository martialArtRepository;

    private CharacterSheetService service;
    private CharacterTechniqueService martial;
    private User master;
    private User player;
    private Campaign campaign;
    private Npc npc;
    private CampaignNpc cn;
    private CharacterSheet sheet;
    private final Map<SheetSection, Boolean> rows = new EnumMap<>(SheetSection.class);

    @BeforeEach
    void setUp() {
        service = new CharacterSheetService(sheetRepository, memberRepository, visibilityRepository, activityLog, events,
                campaignNpcRepository, npcVisibilityRepository, npcRepository, userRepository, ruleService,
                catalogService, buildRepository, imageRepository, rollService);
        martial = new CharacterTechniqueService(sheetRepository, memberRepository, campaignNpcRepository,
                characterArtRepository, techniqueRepository, martialArtRepository, activityLog, events,
                npcVisibilityRepository);
        master = User.builder().id(UUID.randomUUID()).name("Mestre").build();
        player = User.builder().id(UUID.randomUUID()).name("Ana").build();
        campaign = Campaign.builder().id(UUID.randomUUID()).name("Mesa").build();
        npc = Npc.builder().id(UUID.randomUUID()).name("Aldric").ownerId(master.getId())
                .interactions(new ArrayList<>(List.of(new NpcInteraction("Treino", "Sparring", "d", (short) 2)))).build();
        cn = CampaignNpc.builder().id(UUID.randomUUID()).campaign(campaign).npcId(npc.getId()).visible(true).build();
        sheet = CharacterSheet.builder().id(UUID.randomUUID()).npcId(npc.getId()).npcOwnerId(master.getId())
                .name("Aldric").buildStatus(BuildStatus.COMPLETED).bodyType(BodyType.MESOMORPH)
                .dominance(Dominance.RIGHT).heightM(1.8).weightKg(75).combatReady(true)
                .str(10).dex(10).con(12).intl(10).cha(10).men(10).tal(10)
                .hpMax(22).spMax(11).kpMax(4).currentHp(20).currentSp(10).currentKp(3)
                .injuries(new ArrayList<>()).inventory(new ArrayList<>())
                .traits(new ArrayList<>()).clans(new ArrayList<>()).build();
        sheet.getTraits().add(CharacterTrait.builder().name("Forte").build());
        sheet.getClans().add(CharacterClan.builder().name("Clã X").build());
        lenient().when(sheetRepository.findByNpcId(npc.getId())).thenReturn(Optional.of(sheet));
        lenient().when(campaignNpcRepository.findByCampaignIdAndNpcId(campaign.getId(), npc.getId()))
                .thenReturn(Optional.of(cn));
        lenient().when(npcRepository.findById(npc.getId())).thenReturn(Optional.of(npc));
        lenient().when(catalogService.findClan(any())).thenReturn(Optional.empty());
        lenient().when(npcVisibilityRepository.findByCampaignNpcId(cn.getId())).thenAnswer(i -> rows.entrySet().stream()
                .map(e -> NpcSheetVisibility.builder().campaignNpc(cn).section(e.getKey()).visible(e.getValue()).build())
                .toList());
        lenient().when(npcVisibilityRepository.findByCampaignNpcIdAndSection(any(), any())).thenAnswer(i -> {
            SheetSection s = i.getArgument(1);
            return rows.containsKey(s) ? Optional.of(NpcSheetVisibility.builder().campaignNpc(cn).section(s)
                    .visible(rows.get(s)).build()) : Optional.empty();
        });
        lenient().when(npcVisibilityRepository.save(any(NpcSheetVisibility.class))).thenAnswer(i -> {
            NpcSheetVisibility v = i.getArgument(0);
            rows.put(v.getSection(), v.isVisible());
            return v;
        });
        member(master, Role.MASTER);
        member(player, Role.PLAYER);
    }

    private void member(User u, Role role) {
        CampaignMember m = CampaignMember.builder().id(UUID.randomUUID()).campaign(campaign).user(u).role(role).build();
        lenient().when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), u.getId())).thenReturn(Optional.of(m));
    }

    private CharacterSheetResponse asPlayer() {
        return service.getNpcSheetInCampaign(campaign.getId(), npc.getId(), player.getId());
    }

    private void show(SheetSection... sections) {
        for (SheetSection s : sections) rows.put(s, true);
    }

    @Test
    @DisplayName("jogador vê só PQ: HP/SP nulos e derivados de HP omitidos")
    void onlyKpVisible() {
        show(SheetSection.KP, SheetSection.ATTRIBUTES, SheetSection.RADAR);
        CharacterSheetResponse r = asPlayer();
        assertThat(r.resources().currentKp()).isEqualTo(3);
        assertThat(r.resources().kpMax()).isEqualTo(4);
        assertThat(r.resources().currentHp()).isNull();
        assertThat(r.resources().hpMax()).isNull();
        assertThat(r.resources().effectiveHpMax()).isNull();
        assertThat(r.resources().currentSp()).isNull();
        assertThat(r.derived().hpLimiter()).isNull();
        assertThat(r.derived().criticalState()).isNull();
        assertThat(r.derived().spLimiter()).isNull();
        assertThat(r.derived().kpLimiter()).isNotNull();
        assertThat(r.progress()).isNull();
        assertThat(r.fatigue()).isNull();
    }

    @Test
    @DisplayName("RESOURCES antigo liberado equivale a HP, SP e PQ")
    void legacyResources() {
        show(SheetSection.RESOURCES);
        CharacterSheetResponse r = asPlayer();
        assertThat(r.resources().currentHp()).isEqualTo(20);
        assertThat(r.resources().currentSp()).isEqualTo(10);
        assertThat(r.resources().currentKp()).isEqualTo(3);
        assertThat(r.visibleSections()).containsExactly(SheetSection.HP, SheetSection.SP, SheetSection.KP);
    }

    @Test
    @DisplayName("nada liberado: recursos, desgaste, interações e seções nulos")
    void nothingVisible() {
        CharacterSheetResponse r = asPlayer();
        assertThat(r.resources()).isNull();
        assertThat(r.fatigue()).isNull();
        assertThat(r.interactions()).isNull();
        assertThat(r.visibleSections()).isEmpty();
    }

    @Test
    @DisplayName("FATIGUE liberado mostra o desgaste sem mostrar recursos")
    void fatigueOnly() {
        sheet.setNpcTracking(true);
        show(SheetSection.FATIGUE);
        CharacterSheetResponse r = asPlayer();
        assertThat(r.fatigue()).isNotNull();
        assertThat(r.resources()).isNull();
    }

    @Test
    @DisplayName("§53.2-6: NPC sem Cansaço/PS ligado não manda desgaste, mesmo com FATIGUE liberado")
    void fatigueOffByDefault() {
        show(SheetSection.FATIGUE, SheetSection.HP, SheetSection.SP, SheetSection.KP);
        CharacterSheetResponse r = asPlayer();
        assertThat(r.fatigue()).isNull();
        assertThat(r.progress()).isNull();
    }

    @Test
    @DisplayName("CLANS oculto com TRAITS liberado: traços aparecem, clãs não")
    void clansSeparatedFromTraits() {
        show(SheetSection.TRAITS);
        CharacterSheetResponse r = asPlayer();
        assertThat(r.traits()).hasSize(1);
        assertThat(r.clans()).isNull();
        show(SheetSection.CLANS);
        assertThat(asPlayer().clans()).hasSize(1);
    }

    @Test
    @DisplayName("INTERACTIONS: só aparecem quando liberadas")
    void interactions() {
        assertThat(asPlayer().interactions()).isNull();
        show(SheetSection.INTERACTIONS);
        assertThat(asPlayer().interactions()).hasSize(1);
        assertThat(asPlayer().interactions().get(0).name()).isEqualTo("Sparring");
    }

    @Test
    @DisplayName("RADAR oculto anula limitadores e BODY_MAP oculto anula partes perdidas")
    void radarAndBodyMap() {
        show(SheetSection.ATTRIBUTES, SheetSection.HP, SheetSection.INJURIES);
        CharacterSheetResponse r = asPlayer();
        assertThat(r.derived().limiters()).isNull();
        assertThat(r.derived().hpLimiter()).isNull();
        assertThat(r.missingParts()).isNull();
        show(SheetSection.RADAR, SheetSection.BODY_MAP);
        r = asPlayer();
        assertThat(r.derived().limiters()).isNotEmpty();
        assertThat(r.derived().hpLimiter()).isNotNull();
        assertThat(r.missingParts()).isNotNull();
    }

    @Test
    @DisplayName("Mestre vê tudo e visibleSections lista todas as seções de NPC")
    void masterSeesAll() {
        CharacterSheetResponse r = service.getNpcSheetInCampaign(campaign.getId(), npc.getId(), master.getId());
        assertThat(r.resources().currentHp()).isEqualTo(20);
        assertThat(r.clans()).hasSize(1);
        assertThat(r.interactions()).hasSize(1);
        assertThat(r.visibleSections()).containsExactlyElementsOf(SheetSection.NPC_SECTIONS);
    }

    @Test
    @DisplayName("GET visibilidade: lista as seções de NPC na ordem, ausentes como false")
    void listOrder() {
        show(SheetSection.KP);
        List<NpcSectionVisibilityResponse> list = service.listNpcSheetVisibility(campaign.getId(), npc.getId(), master.getId());
        assertThat(list).extracting(NpcSectionVisibilityResponse::section).containsExactlyElementsOf(SheetSection.NPC_SECTIONS);
        assertThat(list).filteredOn(NpcSectionVisibilityResponse::visible).hasSize(1);
    }

    @Test
    @DisplayName("PUT em lote por jogador -> 403")
    void bulkPutByPlayer() {
        assertThatThrownBy(() -> service.updateNpcSheetVisibility(campaign.getId(), npc.getId(),
                new UpdateNpcSheetVisibilityRequest(Map.of(SheetSection.HP, true)), player.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("PUT em lote com seção que não é de NPC -> 400")
    void bulkPutInvalidSection() {
        assertThatThrownBy(() -> service.updateNpcSheetVisibility(campaign.getId(), npc.getId(),
                new UpdateNpcSheetVisibilityRequest(Map.of(SheetSection.LOGS, true)), master.getId()))
                .isInstanceOf(InvalidCharacterSheetException.class);
    }

    @Test
    @DisplayName("PUT em lote altera só as chaves enviadas e avisa a campanha")
    void bulkPutOnlyChangesSent() {
        show(SheetSection.KP, SheetSection.ARTS);
        Map<SheetSection, Boolean> body = new LinkedHashMap<>();
        body.put(SheetSection.HP, true);
        body.put(SheetSection.ARTS, false);
        List<NpcSectionVisibilityResponse> out = service.updateNpcSheetVisibility(campaign.getId(), npc.getId(),
                new UpdateNpcSheetVisibilityRequest(body), master.getId());
        assertThat(rows).containsEntry(SheetSection.HP, true).containsEntry(SheetSection.ARTS, false)
                .containsEntry(SheetSection.KP, true);
        assertThat(out).filteredOn(NpcSectionVisibilityResponse::visible)
                .extracting(NpcSectionVisibilityResponse::section).containsExactly(SheetSection.HP, SheetSection.KP);
        verify(events).publishEvent(any(CampaignUpdateMessage.class));
    }

    // ------------------------------------------------------------------ marcial

    private void moves() {
        CharacterArt art = CharacterArt.builder().id(UUID.randomUUID()).artName("Karatê").build();
        CharacterTechnique tech = CharacterTechnique.builder().id(UUID.randomUUID()).sheet(sheet).characterArt(art)
                .kind(MoveKind.TECHNIQUE).mastery(com.tatakai.manager.martial.entity.Mastery.STANDARD).name("Mae Geri").graduation(Graduation.BEGINNER).borrowedFrom("Judô").build();
        CharacterTechnique base = CharacterTechnique.builder().id(UUID.randomUUID()).sheet(sheet).characterArt(art)
                .kind(MoveKind.BASE).mastery(com.tatakai.manager.martial.entity.Mastery.STANDARD).name("Postura").graduation(Graduation.BEGINNER).build();
        lenient().when(techniqueRepository.findBySheetIdOrderByCreatedAt(sheet.getId())).thenReturn(List.of(tech, base));
        lenient().when(characterArtRepository.findBySheetIdOrderByCreatedAt(sheet.getId())).thenReturn(List.of());
    }

    private SheetMartialResponse martialAsPlayer() {
        return martial.getNpcMartialInCampaign(campaign.getId(), npc.getId(), player.getId());
    }

    @Test
    @DisplayName("marcial: nada liberado -> vazio; TECHNIQUES só técnicas; BASES só bases")
    void martialFiltering() {
        moves();
        assertThat(martialAsPlayer().techniques()).isEmpty();
        show(SheetSection.TECHNIQUES);
        assertThat(martialAsPlayer().techniques()).extracting(t -> t.name()).containsExactly("Mae Geri");
        rows.clear();
        show(SheetSection.BASES);
        assertThat(martialAsPlayer().techniques()).extracting(t -> t.name()).containsExactly("Postura");
    }

    @Test
    @DisplayName("marcial: sem ARTS as técnicas saem sem a arte (characterArtId e borrowedFrom nulos)")
    void martialHidesArt() {
        moves();
        show(SheetSection.TECHNIQUES);
        var t = martialAsPlayer().techniques().get(0);
        assertThat(t.characterArtId()).isNull();
        assertThat(t.borrowedFrom()).isNull();
        assertThat(martialAsPlayer().arts()).isEmpty();
    }

    @Test
    @DisplayName("marcial: Mestre vê tudo; ficha leve devolve vazio")
    void martialMasterAndLight() {
        moves();
        assertThat(martial.getNpcMartialInCampaign(campaign.getId(), npc.getId(), master.getId()).techniques()).hasSize(2);
        sheet.setCombatReady(false);
        assertThat(martial.getNpcMartialInCampaign(campaign.getId(), npc.getId(), master.getId()).techniques()).isEmpty();
    }
}
