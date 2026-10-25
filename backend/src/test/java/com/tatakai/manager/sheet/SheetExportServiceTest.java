package com.tatakai.manager.sheet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tatakai.manager.dto.response.SheetExportFile;
import com.tatakai.manager.dto.response.SheetImportResponse;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.CharacterSheetAlreadyExistsException;
import com.tatakai.manager.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.exception.InvalidCharacterSheetException;
import com.tatakai.manager.repository.*;
import com.tatakai.manager.service.CharacterSheetService;
import com.tatakai.manager.service.SheetActivityLogService;
import com.tatakai.manager.service.SheetExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("SheetExportService — F7 exportar/importar ficha")
class SheetExportServiceTest {

    @Mock private CharacterSheetRepository sheetRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private CharacterSheetVisibilityRepository visibilityRepository;
    @Mock private CharacterArtRepository artRepository;
    @Mock private CharacterTechniqueRepository techniqueRepository;
    @Mock private CharacterInstinctRepository instinctRepository;
    @Mock private RivalRepository rivalRepository;
    @Mock private UserRepository userRepository;
    @Mock private TraitDefinitionRepository traitCatalog;
    @Mock private ClanRepository clanCatalog;
    @Mock private MartialArtRepository artCatalog;
    @Mock private InstinctRepository instinctCatalog;
    @Mock private SheetActivityLogService activityLog;
    @Mock private CharacterSheetService sheetService;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private SheetExportService service;

    private Campaign campaign;
    private User masterUser, playerUser, otherUser;
    private CampaignMember masterMember, playerMember, otherMember;
    private CharacterSheet sheet;

    @BeforeEach
    void setUp() {
        service = new SheetExportService(sheetRepository, memberRepository, visibilityRepository, artRepository,
                techniqueRepository, instinctRepository, rivalRepository, userRepository, traitCatalog, clanCatalog,
                artCatalog, instinctCatalog, activityLog, sheetService, mapper);
        campaign = Campaign.builder().id(UUID.randomUUID()).name("Mesa").build();
        masterUser = user("Mestre");
        playerUser = user("Ana");
        otherUser = user("Bia");
        masterMember = member(masterUser, Role.MASTER);
        playerMember = member(playerUser, Role.PLAYER);
        otherMember = member(otherUser, Role.PLAYER);
        sheet = CharacterSheet.builder().id(UUID.randomUUID()).campaignMember(playerMember).name("Kaito")
                .buildStatus(BuildStatus.COMPLETED).bodyType(BodyType.MESOMORPH).dominance(Dominance.RIGHT)
                .heightM(1.8).weightKg(75).str(10).dex(11).con(12).intl(13).cha(14).men(15).tal(16)
                .hpMax(22).spMax(11).kpMax(4).currentHp(20).currentSp(11).currentKp(4).money(50)
                .injuries(new ArrayList<>()).inventory(new ArrayList<>())
                .build();
        sheet.getInventory().add(InventoryItem.builder().sheet(sheet).name("Faca").quantity(1).build());
        lenient().when(sheetRepository.findById(sheet.getId())).thenReturn(Optional.of(sheet));
        lenient().when(sheetRepository.save(any(CharacterSheet.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(artRepository.save(any(CharacterArt.class))).thenAnswer(i -> i.getArgument(0));
    }

    private User user(String name) {
        return User.builder().id(UUID.randomUUID()).name(name).email(name + "@rpg.com").build();
    }

    private CampaignMember member(User u, Role role) {
        CampaignMember m = CampaignMember.builder().id(UUID.randomUUID()).campaign(campaign).user(u).role(role).build();
        lenient().when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), u.getId())).thenReturn(Optional.of(m));
        return m;
    }

    private byte[] json(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ exportar

    @Test
    @DisplayName("dono e Mestre exportam; grava log BUILD/EXPORT")
    void exportOwnerAndMaster() {
        SheetExportFile f = service.export(sheet.getId(), playerUser.getId());
        assertThat(f.schemaVersion()).isEqualTo(1);
        assertThat(f.sheet().name()).isEqualTo("Kaito");
        assertThat(f.sheet().attributes()).containsEntry(Attribute.CON, 12);
        assertThat(f.sheet().inventory()).hasSize(1);
        assertThat(f.source().campaignId()).isEqualTo(campaign.getId());
        verify(activityLog).record(any(CharacterSheet.class), eq(playerUser.getId()), eq(ActorRole.PLAYER),
                eq(ActivityCategory.BUILD), eq("EXPORT"), any(), any(), any(), any(), any());

        assertThat(service.export(sheet.getId(), masterUser.getId()).sheet().name()).isEqualTo("Kaito");
    }

    @Test
    @DisplayName("outro jogador e não-membro recebem 404")
    void exportOthersNotFound() {
        assertThatThrownBy(() -> service.export(sheet.getId(), otherUser.getId()))
                .isInstanceOf(CharacterSheetNotFoundException.class);
        assertThatThrownBy(() -> service.export(sheet.getId(), UUID.randomUUID()))
                .isInstanceOf(CharacterSheetNotFoundException.class);
        verify(activityLog, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("seções ocultas pelo Mestre ficam de fora para o jogador, mas não para o Mestre")
    void exportOmitsHiddenByMaster() {
        lenient().when(visibilityRepository.findByCampaignMemberId(playerMember.getId())).thenReturn(List.of(
                CharacterSheetVisibility.builder().section(SheetSection.ATTRIBUTES).hiddenByMaster(true).build(),
                CharacterSheetVisibility.builder().section(SheetSection.INVENTORY).hiddenByMaster(true).build()));

        SheetExportFile player = service.export(sheet.getId(), playerUser.getId());
        assertThat(player.sheet().attributes()).isNull();
        assertThat(player.sheet().inventory()).isNull();
        assertThat(player.sheet().money()).isNull();
        assertThat(player.sheet().hpMax()).isEqualTo(22);

        SheetExportFile master = service.export(sheet.getId(), masterUser.getId());
        assertThat(master.sheet().attributes()).isNotNull();
        assertThat(master.sheet().inventory()).hasSize(1);
    }

    @Test
    @DisplayName("ficha de NPC: só o dono exporta")
    void exportNpcSheet() {
        UUID owner = UUID.randomUUID();
        CharacterSheet npc = CharacterSheet.builder().id(UUID.randomUUID()).npcId(UUID.randomUUID()).npcOwnerId(owner)
                .name("Rival").injuries(new ArrayList<>()).inventory(new ArrayList<>()).build();
        lenient().when(sheetRepository.findById(npc.getId())).thenReturn(Optional.of(npc));
        assertThat(service.export(npc.getId(), owner).source().npcId()).isEqualTo(npc.getNpcId());
        assertThatThrownBy(() -> service.export(npc.getId(), masterUser.getId()))
                .isInstanceOf(CharacterSheetNotFoundException.class);
    }

    // ------------------------------------------------------------------ importar

    @Test
    @DisplayName("importar: ida e volta cria ficha PENDING_APPROVAL com os dados do arquivo")
    void importHappyPath() throws Exception {
        byte[] bytes = mapper.writeValueAsBytes(service.export(sheet.getId(), playerUser.getId()));
        UUID newcomer = otherUser.getId();

        SheetImportResponse r = service.importSheet(campaign.getId(), bytes, "kaito.tatakai.json", newcomer);

        ArgumentCaptor<CharacterSheet> cap = ArgumentCaptor.forClass(CharacterSheet.class);
        verify(sheetRepository).save(cap.capture());
        CharacterSheet s = cap.getValue();
        assertThat(s.getBuildStatus()).isEqualTo(BuildStatus.PENDING_APPROVAL);
        assertThat(s.getCampaignMember()).isSameAs(otherMember);
        assertThat(s.getName()).isEqualTo("Kaito");
        assertThat(s.getCon()).isEqualTo(12);
        assertThat(s.getCurrentHp()).isEqualTo(20);
        assertThat(s.getInventory()).hasSize(1);
        assertThat(r.warnings()).isEmpty();
        verify(activityLog).record(any(CharacterSheet.class), eq(newcomer), eq(ActorRole.PLAYER),
                eq(ActivityCategory.BUILD), eq("IMPORT"), any(), any(), eq("kaito.tatakai.json"), any(), any());
    }

    @Test
    @DisplayName("importar: avisos para catálogo ausente, versão diferente e atual > máximo (cortado)")
    void importWarnings() {
        UUID traitId = UUID.randomUUID();
        UUID clanId = UUID.randomUUID();
        lenient().when(clanCatalog.findById(clanId)).thenReturn(Optional.of(Clan.builder().id(clanId).version(5).build()));
        String body = """
                {"schemaVersion":1,
                 "sheet":{"name":"Zed","attributes":{"STR":1500,"DEX":10,"CON":10,"INT":10,"CHA":10,"MEN":10,"TAL":10},
                          "hpMax":20,"spMax":10,"kpMax":3,"currentHp":99,"currentSp":10,"currentKp":3},
                 "traits":[{"traitId":"%s","traitVersion":1,"name":"Sumido"}],
                 "clans":[{"clanId":"%s","clanVersion":1,"name":"Velho"}],
                 "arts":[{"artId":"%s","artName":"Arte X","techniques":[{"name":"Golpe"}]}],
                 "instincts":[{"instinctId":"%s","name":"Faro"}]}
                """.formatted(traitId, clanId, UUID.randomUUID(), UUID.randomUUID());

        SheetImportResponse r = service.importSheet(campaign.getId(), json(body), "z.json", otherUser.getId());

        assertThat(r.warnings()).hasSize(6)
                .anyMatch(w -> w.contains("STR")).anyMatch(w -> w.contains("HP atual"))
                .anyMatch(w -> w.contains("Sumido")).anyMatch(w -> w.contains("Velho"))
                .anyMatch(w -> w.contains("Arte X")).anyMatch(w -> w.contains("Faro"));
        ArgumentCaptor<CharacterSheet> cap = ArgumentCaptor.forClass(CharacterSheet.class);
        verify(sheetRepository).save(cap.capture());
        assertThat(cap.getValue().getCurrentHp()).isEqualTo(20);
        assertThat(cap.getValue().getStr()).isEqualTo(999);
        assertThat(cap.getValue().getTraits()).hasSize(1); // snapshot mantido
        verify(techniqueRepository).save(any(CharacterTechnique.class));
    }

    @Test
    @DisplayName("importar: 409 se já tem ficha, 403 para Mestre e não membro")
    void importGuards() {
        org.mockito.Mockito.when(sheetRepository.existsByCampaignMemberId(playerMember.getId())).thenReturn(true);
        byte[] ok = json("{\"schemaVersion\":1,\"sheet\":{\"name\":\"A\"}}");
        assertThatThrownBy(() -> service.importSheet(campaign.getId(), ok, "a.json", playerUser.getId()))
                .isInstanceOf(CharacterSheetAlreadyExistsException.class);
        assertThatThrownBy(() -> service.importSheet(campaign.getId(), ok, "a.json", masterUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.importSheet(campaign.getId(), ok, "a.json", UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        verify(sheetRepository, never()).save(any());
    }

    @Test
    @DisplayName("importar: 400 para schemaVersion errado, JSON inválido, grande demais e extensão errada")
    void importInvalid() {
        UUID u = otherUser.getId();
        assertThatThrownBy(() -> service.importSheet(campaign.getId(),
                json("{\"schemaVersion\":2,\"sheet\":{\"name\":\"A\"}}"), "a.json", u))
                .isInstanceOf(InvalidCharacterSheetException.class).hasMessageContaining("2");
        assertThatThrownBy(() -> service.importSheet(campaign.getId(), json("não é json"), "a.json", u))
                .isInstanceOf(InvalidCharacterSheetException.class);
        assertThatThrownBy(() -> service.importSheet(campaign.getId(), new byte[1024 * 1024 + 1], "a.json", u))
                .isInstanceOf(InvalidCharacterSheetException.class).hasMessageContaining("1 MB");
        assertThatThrownBy(() -> service.importSheet(campaign.getId(),
                json("{\"schemaVersion\":1,\"sheet\":{\"name\":\"A\"}}"), "a.txt", u))
                .isInstanceOf(InvalidCharacterSheetException.class);
        verify(sheetRepository, never()).save(any());
    }
}
