package com.tatakai.manager.build;

import com.tatakai.manager.dto.request.CatalogImportRequest;
import com.tatakai.manager.dto.request.ClanPayload;
import com.tatakai.manager.dto.request.TraitPayload;
import com.tatakai.manager.dto.response.CatalogImportResponse;
import com.tatakai.manager.dto.response.TraitDefinitionResponse;
import com.tatakai.manager.entity.Clan;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.entity.TraitCategory;
import com.tatakai.manager.entity.TraitDefinition;
import com.tatakai.manager.entity.TraitRarity;
import com.tatakai.manager.entity.User;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.ClanRepository;
import com.tatakai.manager.repository.MartialArtRepository;
import com.tatakai.manager.repository.InstinctRepository;
import com.tatakai.manager.dto.request.FightMilestonePayload;
import com.tatakai.manager.dto.request.InstinctPayload;
import com.tatakai.manager.entity.Instinct;
import com.tatakai.manager.entity.KeikenRank;
import com.tatakai.manager.entity.MilestoneCategory;
import com.tatakai.manager.repository.FightMilestoneRepository;
import com.tatakai.manager.repository.TraitDefinitionRepository;
import com.tatakai.manager.repository.UserRepository;
import com.tatakai.manager.service.CatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogService — Sprint 10 — F2 Construção Inicial")
class CatalogServiceTest {

    @Mock private TraitDefinitionRepository traitRepository;
    @Mock private ClanRepository clanRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private MartialArtRepository artRepository;
    @Mock private InstinctRepository instinctRepository;
    @Mock private FightMilestoneRepository milestoneRepository;

    private CatalogService service;
    private final UUID cid = UUID.randomUUID();
    private final UUID editor = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
    private final UUID master = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CatalogService(traitRepository, clanRepository, memberRepository, userRepository,
                " Admin@Tatakai.com , outro@x.com ,", artRepository, instinctRepository, milestoneRepository);
        lenient().when(userRepository.findById(editor)).thenReturn(Optional.of(
                User.builder().id(editor).email("  ADMIN@tatakai.COM ").build()));
        lenient().when(userRepository.findById(outsider)).thenReturn(Optional.of(
                User.builder().id(outsider).email("nao@x.com").build()));
        lenient().when(memberRepository.existsByCampaignIdAndUserIdAndRole(cid, master, Role.MASTER)).thenReturn(true);
        lenient().when(traitRepository.save(any(TraitDefinition.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(clanRepository.save(any(Clan.class))).thenAnswer(i -> i.getArgument(0));
    }

    private TraitPayload tp(String name, TraitRarity r, Integer roll) {
        return new TraitPayload(name, r, TraitCategory.PHYSICAL, roll, "d", List.of(), null, false, false, null, null, null, null);
    }

    private TraitDefinition trait(String name, TraitRarity r, Integer roll, UUID campaign) {
        return TraitDefinition.builder().id(UUID.randomUUID()).campaignId(campaign).name(name).rarity(r)
                .rollNumber(roll).version(1).build();
    }

    private Clan clan(String name, Integer roll, UUID campaign) {
        return Clan.builder().id(UUID.randomUUID()).campaignId(campaign).name(name).rollNumber(roll).version(1).build();
    }

    @Test
    @DisplayName("editor global: e-mail comparado sem caixa e sem espaços")
    void globalEditor_allowed() {
        TraitDefinitionResponse r = service.createTrait(null, tp("Forte", TraitRarity.COMMON, 1), editor);
        assertThat(r.campaignId()).isNull();
        assertThat(r.version()).isEqualTo(1);
        assertThat(service.permissions(editor).globalEditor()).isTrue();
        assertThat(service.permissions(outsider).globalEditor()).isFalse();
    }

    @Test
    @DisplayName("não-editor não escreve no catálogo global")
    void globalEditor_denied() {
        assertThatThrownBy(() -> service.createTrait(null, tp("Forte", TraitRarity.COMMON, 1), outsider))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.createClan(null,
                new ClanPayload("Uchiha", 1, null, null, null), outsider))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.importCatalog(null, new CatalogImportRequest(List.of(), List.of(), List.of(), null, null), outsider))
                .isInstanceOf(AccessDeniedException.class);
        verify(traitRepository, never()).save(any());
        verify(clanRepository, never()).save(any());
    }

    @Test
    @DisplayName("sem lista de editores configurada ninguém edita o global")
    void emptyEditors() {
        CatalogService s = new CatalogService(traitRepository, clanRepository, memberRepository, userRepository, "", artRepository, instinctRepository, milestoneRepository);
        assertThat(s.permissions(editor).globalEditor()).isFalse();
    }

    @Test
    @DisplayName("Mestre escreve no catálogo da campanha; jogador não")
    void campaignWrite() {
        TraitDefinitionResponse r = service.createTrait(cid, tp("Local", TraitRarity.RARE, 2), master);
        assertThat(r.campaignId()).isEqualTo(cid);

        assertThatThrownBy(() -> service.createTrait(cid, tp("Local", TraitRarity.RARE, 2), player))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.createClan(cid, new ClanPayload("X", 1, null, null, null), player))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("traitsForCampaign: traço da campanha substitui o global com mesma (raridade, número)")
    void traitsForCampaign_override() {
        TraitDefinition own = trait("Custom R5", TraitRarity.RARE, 5, cid);
        TraitDefinition replaced = trait("Global R5", TraitRarity.RARE, 5, null);
        TraitDefinition otherRarity = trait("Global C5", TraitRarity.COMMON, 5, null);
        TraitDefinition otherNumber = trait("Global R6", TraitRarity.RARE, 6, null);
        TraitDefinition noNumber = trait("Global X", TraitRarity.RARE, null, null);
        when(traitRepository.findByCampaignId(cid)).thenReturn(List.of(own));
        when(traitRepository.findByCampaignIdIsNull()).thenReturn(List.of(replaced, otherRarity, otherNumber, noNumber));

        List<TraitDefinition> out = service.traitsForCampaign(cid);

        assertThat(out).extracting(TraitDefinition::getName)
                .containsExactlyInAnyOrder("Custom R5", "Global C5", "Global R6", "Global X");
    }

    @Test
    @DisplayName("clansForCampaign: clã da campanha substitui o global com mesmo número")
    void clansForCampaign_override() {
        Clan own = clan("Custom 3", 3, cid);
        when(clanRepository.findByCampaignId(cid)).thenReturn(List.of(own));
        when(clanRepository.findByCampaignIdIsNull()).thenReturn(List.of(
                clan("Global 3", 3, null), clan("Global 4", 4, null), clan("Global livre", null, null)));

        assertThat(service.clansForCampaign(cid)).extracting(Clan::getName)
                .containsExactlyInAnyOrder("Custom 3", "Global 4", "Global livre");
    }

    @Test
    @DisplayName("RN-TRAIT-02: atualizar traço incrementa a versão")
    void update_incrementsVersion() {
        TraitDefinition t = trait("Velho", TraitRarity.EPIC, 9, null);
        t.setVersion(4);
        when(traitRepository.findById(t.getId())).thenReturn(Optional.of(t));

        TraitDefinitionResponse r = service.updateTrait(t.getId(), tp("Novo", TraitRarity.EPIC, 9), editor);

        assertThat(r.version()).isEqualTo(5);
        assertThat(r.name()).isEqualTo("Novo");
    }

    @Test
    @DisplayName("atualizar clã incrementa a versão")
    void updateClan_incrementsVersion() {
        Clan c = clan("Velho", 2, cid);
        c.setVersion(7);
        when(clanRepository.findById(c.getId())).thenReturn(Optional.of(c));

        service.updateClan(c.getId(), new ClanPayload("Novo", 2, null, null, null), master);

        assertThat(c.getVersion()).isEqualTo(8);
    }

    @Test
    @DisplayName("importCatalog: upsert por nome (+raridade) e contagem")
    void importCatalog_upsert() {
        TraitDefinition existing = trait("A", TraitRarity.RARE, 1, null);
        Clan existingClan = clan("Cla A", 1, null);
        when(traitRepository.findByCampaignIdIsNull()).thenReturn(List.of(existing));
        when(clanRepository.findByCampaignIdIsNull()).thenReturn(List.of(existingClan));

        CatalogImportResponse r = service.importCatalog(null, new CatalogImportRequest(
                List.of(tp("A", TraitRarity.RARE, 1), tp("B", TraitRarity.RARE, 2), tp("A", TraitRarity.COMMON, 3)),
                List.of(new ClanPayload("Cla A", 1, "x", null, null), new ClanPayload("Cla B", 2, null, null, null)), null, null, null),
                editor);

        assertThat(r.traits()).isEqualTo(3);
        assertThat(r.clans()).isEqualTo(2);
        ArgumentCaptor<TraitDefinition> cap = ArgumentCaptor.forClass(TraitDefinition.class);
        verify(traitRepository, times(3)).save(cap.capture());
        assertThat(cap.getAllValues().get(0)).isSameAs(existing);
        assertThat(existing.getVersion()).isEqualTo(2);
        assertThat(cap.getAllValues().get(1)).isNotSameAs(existing);
        assertThat(cap.getAllValues().get(1).getVersion()).isEqualTo(1);
        assertThat(cap.getAllValues().get(2).getId()).isNotEqualTo(existing.getId());
        verify(clanRepository, times(2)).save(any(Clan.class));
        assertThat(existingClan.getDescription()).isEqualTo("x");
    }

    @Test
    @DisplayName("F4b — Instintos e Marcos: importação por nome e item da campanha substitui o global")
    void instinctsAndMilestones() {
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(cid, master, Role.MASTER)).thenReturn(true);
        CatalogImportResponse r = service.importCatalog(cid, new CatalogImportRequest(null, null, null,
                List.of(new InstinctPayload("Aquecido", KeikenRank.SEASONED, "+2 Adrenalina inicial")),
                List.of(new FightMilestonePayload("Beira do Abismo", MilestoneCategory.DISADVANTAGE, null,
                        "CRITICAL_STATE", null, null, null))), master);
        assertThat(r.instincts()).isEqualTo(1);
        assertThat(r.milestones()).isEqualTo(1);
        verify(instinctRepository).save(argThat(i -> cid.equals(i.getCampaignId()) && i.getVersion() == 1));

        Instinct global = Instinct.builder().id(UUID.randomUUID()).name("Aquecido").rank(KeikenRank.NOVICE).build();
        Instinct other = Instinct.builder().id(UUID.randomUUID()).name("Outro").rank(KeikenRank.NOVICE).build();
        Instinct own = Instinct.builder().id(UUID.randomUUID()).campaignId(cid).name("Aquecido")
                .rank(KeikenRank.SEASONED).build();
        when(instinctRepository.findByCampaignIdIsNull()).thenReturn(List.of(global, other));
        when(instinctRepository.findByCampaignId(cid)).thenReturn(List.of(own));
        assertThat(service.instinctsFor(cid)).containsExactlyInAnyOrder(own, other);
    }
}
