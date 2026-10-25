package com.tatakai.manager.sheet;

import com.tatakai.manager.dto.request.CampaignRollRequest;
import com.tatakai.manager.dto.request.RollOverride;
import com.tatakai.manager.dto.request.RollRequest;
import com.tatakai.manager.dto.request.RollTestRequest;
import com.tatakai.manager.dto.response.RollMessage;
import com.tatakai.manager.dto.response.RollProfileResponse;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.InvalidRollException;
import com.tatakai.manager.exception.RollNotFoundException;
import com.tatakai.manager.repository.*;
import com.tatakai.manager.service.RollProfileService;
import com.tatakai.manager.service.RollService;
import com.tatakai.manager.service.SheetActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RollService/RollProfileService — F5 canais de rolagem e RollProfile")
class RollChannelServiceTest {

    @Mock private RollRepository rollRepository;
    @Mock private CharacterSheetRepository sheetRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private SheetActivityLogService activityLog;
    @Mock private ApplicationEventPublisher events;
    @Mock private CharacterTechniqueRepository techniqueRepository;
    @Mock private MartialArtRepository martialArtRepository;

    private RollService service;
    private Campaign campaign;
    private User player;
    private User master;
    private User other;
    private CharacterSheet sheet;
    private CharacterTechnique technique;
    private CharacterTechnique base;

    @BeforeEach
    void setUp() {
        service = new RollService(rollRepository, sheetRepository, memberRepository, userRepository, activityLog,
                events, new RollProfileService(techniqueRepository, martialArtRepository), null);
        campaign = Campaign.builder().id(UUID.randomUUID()).build();
        player = User.builder().id(UUID.randomUUID()).name("Ana").build();
        master = User.builder().id(UUID.randomUUID()).name("Mestre").build();
        other = User.builder().id(UUID.randomUUID()).name("Bia").build();
        CampaignMember pm = member(player, Role.PLAYER);
        member(master, Role.MASTER);
        member(other, Role.PLAYER);
        // STR 30 (mod +3, E=1), DEX 20 (mod +2)
        sheet = CharacterSheet.builder().id(UUID.randomUUID()).name("Kenji").campaignMember(pm)
                .str(30).dex(20).con(10).build();
        lenient().when(sheetRepository.findById(sheet.getId())).thenReturn(Optional.of(sheet));
        lenient().when(rollRepository.save(any(Roll.class))).thenAnswer(i -> i.getArgument(0));

        CharacterArt art = CharacterArt.builder().id(UUID.randomUUID()).artId(UUID.randomUUID()).sheet(sheet).build();
        technique = CharacterTechnique.builder().id(UUID.randomUUID()).sheet(sheet).characterArt(art)
                .kind(MoveKind.TECHNIQUE).name("Oi-Zuki").state(TechniqueState.ACTIVE).mastery(Mastery.STANDARD)
                .bonuses(List.of(MasteryBonus.HIT, MasteryBonus.HIT, MasteryBonus.DAMAGE, MasteryBonus.COST)).build();
        base = CharacterTechnique.builder().id(UUID.randomUUID()).sheet(sheet).characterArt(art)
                .kind(MoveKind.BASE).name("Postura").state(TechniqueState.ACTIVE).mastery(Mastery.STANDARD).build();

        ArtTechnique cat = new ArtTechnique();
        cat.setName("Oi-Zuki");
        cat.setSpCostBase(10);
        AssemblyVariation v = new AssemblyVariation();
        v.setName("Padrão");
        v.setItems(List.of(item("Dado Maior", 5), item("Precisão", 2), item("Outro", 3)));
        cat.setVariations(List.of(v));
        ArtBase artBase = new ArtBase();
        artBase.setName("Postura");
        artBase.setItems(List.of(item("Precisão", 3), item("Força do Golpe", 4)));
        MartialArt ma = MartialArt.builder().id(art.getArtId()).techniques(List.of(cat)).bases(List.of(artBase)).build();
        lenient().when(martialArtRepository.findById(art.getArtId())).thenReturn(Optional.of(ma));
        lenient().when(techniqueRepository.findBySheetIdOrderByCreatedAt(sheet.getId()))
                .thenReturn(List.of(technique, base));
        lenient().when(techniqueRepository.findById(technique.getId())).thenReturn(Optional.of(technique));
        lenient().when(techniqueRepository.findById(base.getId())).thenReturn(Optional.of(base));
    }

    private static AssemblyItem item(String module, int qty) {
        AssemblyItem i = new AssemblyItem();
        i.setModule(module);
        i.setQty(qty);
        i.setKind(AssemblyItemKind.MODULE);
        return i;
    }

    private CampaignMember member(User u, Role role) {
        CampaignMember m = CampaignMember.builder().id(UUID.randomUUID()).campaign(campaign).user(u).role(role).build();
        lenient().when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), u.getId())).thenReturn(Optional.of(m));
        lenient().when(memberRepository.existsByCampaignIdAndUserIdAndRole(campaign.getId(), u.getId(), role))
                .thenReturn(true);
        lenient().when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        return m;
    }

    private RollTestRequest technique(int advantage, boolean targetDowned, UUID baseId, List<RollOverride> ov,
                                      String reason, RollChannel channel) {
        return new RollTestRequest(RollSourceType.TECHNIQUE, technique.getId(), null, null, null,
                RollMode.DETAILED, channel, null, null, "golpe", advantage, baseId, targetDowned, false, null, null,
                ov, reason);
    }

    private static int sum(RollMessage m, String part) {
        return m.breakdown().stream().filter(p -> part.equals(p.part())).mapToInt(RollPart::value).sum();
    }

    @Test
    @DisplayName("perfil da técnica: Dado Maior 5 = 2d6 e E=1 dobra os dados; Precisão, HIT, DAMAGE e SP")
    void profile_technique() {
        List<RollProfileResponse> ps = service.profiles(sheet.getId(), player.getId());

        RollProfileResponse p = ps.stream().filter(x -> x.sourceType() == RollSourceType.TECHNIQUE).findFirst().orElseThrow();
        assertThat(p.name()).isEqualTo("Oi-Zuki");
        assertThat(p.variation()).isEqualTo("Padrão");
        assertThat(p.damageDice().count()).isEqualTo(4); // 2d6 x (1+E=2)
        assertThat(p.damageDice().sides()).isEqualTo(6);
        // acerto: DES +2, Precisão 2, HIT x2
        assertThat(p.hit().stream().mapToInt(RollPart::value).sum()).isEqualTo(6);
        assertThat(p.hitFormula()).isEqualTo("1d20+6");
        // dano: FOR +3, DAMAGE +2
        assertThat(p.damageFormula()).isEqualTo("4d6+5");
        assertThat(p.spCost()).isEqualTo(8); // 10 - 2*COST
        assertThat(p.critMargin()).isEqualTo(20);
        assertThat(ps.stream().filter(x -> x.sourceType() == RollSourceType.BASIC_ATTACK)).hasSize(6);
    }

    @Test
    @DisplayName("APEX soma 1 ao E; técnica em Esboço dá -1 de vantagem")
    void profile_apexAndSketch() {
        technique.setBonuses(List.of(MasteryBonus.APEX));
        technique.setMastery(Mastery.SKETCH);

        RollProfileResponse p = service.profiles(sheet.getId(), player.getId()).get(0);

        assertThat(p.damageDice().count()).isEqualTo(6); // 2d6 x (1+1+1)
        assertThat(p.advantage()).isEqualTo(-1);
    }

    @Test
    @DisplayName("ataque básico: Joelhada 1d4+1 com -2 de acerto (Ataques Básicos); E dobra os dados")
    void profile_basicKnee() {
        RollProfileResponse p = service.profiles(sheet.getId(), player.getId()).stream()
                .filter(x -> x.basicAttack() == BasicAttack.JOELHADA).findFirst().orElseThrow();

        assertThat(p.damageDice().count()).isEqualTo(2);
        assertThat(p.damageDice().sides()).isEqualTo(4);
        assertThat(p.hitFormula()).isEqualTo("1d20"); // DES +2, -2
        assertThat(p.damageFormula()).isEqualTo("2d4+4"); // FOR +3, +1
    }

    @RepeatedTest(20)
    @DisplayName("teste de técnica: total = d20 natural + bônus; crítico/falha seguem o natural; dano soma as parcelas")
    void testRoll_technique() {
        RollMessage m = service.testRoll(sheet.getId(), technique(0, false, null, null, null, null), player.getId());

        assertThat(m.total()).isEqualTo(m.naturalD20() + 6);
        assertThat(m.critical()).isEqualTo(m.naturalD20() == 20);
        assertThat(m.fumble()).isEqualTo(m.naturalD20() == 1);
        assertThat(m.advantage()).isZero();
        int diceCount = m.critical() ? 8 : 4;
        assertThat(m.diceResults()).hasSize(1 + diceCount);
        assertThat(m.damageTotal()).isEqualTo(sum(m, "DAMAGE"))
                .isEqualTo(m.diceResults().subList(1, 1 + diceCount).stream().mapToInt(Integer::intValue).sum() + 5);
        assertThat(m.channel()).isEqualTo(RollChannel.PUBLIC);
        assertThat(m.edited()).isFalse();
        verify(events).publishEvent(any(RollMessage.class));
    }

    @RepeatedTest(20)
    @DisplayName("vantagem líquida +3: 2d20 pega o maior e soma +2")
    void testRoll_advantage() {
        RollMessage m = service.testRoll(sheet.getId(), technique(3, false, null, null, null, null), player.getId());

        RollPart d20 = m.breakdown().get(0);
        assertThat(d20.dice()).hasSize(2);
        assertThat(m.naturalD20()).isEqualTo(Math.max(d20.dice().get(0), d20.dice().get(1)));
        assertThat(m.total()).isEqualTo(m.naturalD20() + 2 + 6);
    }

    @RepeatedTest(20)
    @DisplayName("alvo derrubado +1 vantagem; desvantagem -3 pega o menor e subtrai 2")
    void testRoll_targetDownedAndDisadvantage() {
        RollMessage up = service.testRoll(sheet.getId(), technique(0, true, null, null, null, null), player.getId());
        assertThat(up.advantage()).isEqualTo(1);
        assertThat(up.breakdown().get(0).dice()).hasSize(2);
        assertThat(up.total()).isEqualTo(up.naturalD20() + 6); // +1 vantagem: bônus 0

        RollMessage down = service.testRoll(sheet.getId(), technique(-3, false, null, null, null, null), player.getId());
        RollPart d20 = down.breakdown().get(0);
        assertThat(down.naturalD20()).isEqualTo(Math.min(d20.dice().get(0), d20.dice().get(1)));
        assertThat(down.total()).isEqualTo(down.naturalD20() - 2 + 6);
    }

    @Test
    @DisplayName("base escolhida soma Precisão ao acerto e Força do Golpe ao dano")
    void testRoll_withBase() {
        RollMessage m = service.testRoll(sheet.getId(), technique(0, false, base.getId(), null, null, null), player.getId());

        assertThat(m.total()).isEqualTo(m.naturalD20() + 6 + 3);
        assertThat(m.damageTotal()).isEqualTo(sum(m, "DAMAGE"));
        assertThat(m.breakdown()).anyMatch(p -> p.source().startsWith("Força do Golpe") && p.value() == 4);
    }

    @Test
    @DisplayName("base em Esboço: Precisão pela metade (floor)")
    void testRoll_sketchBase() {
        base.setMastery(Mastery.SKETCH);

        RollMessage m = service.testRoll(sheet.getId(), technique(0, false, base.getId(), null, null, null), player.getId());

        assertThat(m.total()).isEqualTo(m.naturalD20() + 6 + 1);
    }

    @Test
    @DisplayName("ataque básico: Soco rola 2d3 (E=1) e teste de atributo não tem dano")
    void testRoll_basicAndAttribute() {
        RollMessage punch = service.testRoll(sheet.getId(), new RollTestRequest(RollSourceType.BASIC_ATTACK, null,
                null, BasicAttack.SOCO, null, RollMode.QUICK, null, null, null, null, 0, null, false, false, null, null,
                null, null), player.getId());
        assertThat(punch.sourceName()).isEqualTo("Soco");
        int dice = punch.critical() ? 4 : 2; // crítico dobra os dados
        assertThat(punch.damageTotal()).isBetween(dice * 1 + 3, dice * 3 + 3);
        assertThat(punch.total()).isEqualTo(punch.naturalD20() + 2);

        RollMessage test = service.testRoll(sheet.getId(), new RollTestRequest(RollSourceType.ATTRIBUTE_TEST, null,
                null, null, Attribute.STR, RollMode.QUICK, null, null, null, null, 0, null, false, false, null, null,
                null, null), player.getId());
        assertThat(test.damageTotal()).isNull();
        assertThat(test.total()).isEqualTo(test.naturalD20() + 3);
    }

    @Test
    @DisplayName("Desgaste Físico penaliza o acerto (Esgotado -2)")
    void testRoll_fatiguePenalty() {
        sheet.setPhysicalFatigue(7);

        RollMessage m = service.testRoll(sheet.getId(), technique(0, false, null, null, null, null), player.getId());

        assertThat(m.total()).isEqualTo(m.naturalD20() + 6 - 2);
    }

    @Test
    @DisplayName("override troca o valor, marca editado e guarda o motivo")
    void testRoll_override() {
        RollMessage m = service.testRoll(sheet.getId(), technique(0, false, null,
                List.of(new RollOverride("DES", 10), new RollOverride("Bênção", 1)), "regra da casa", null),
                player.getId());

        assertThat(m.total()).isEqualTo(m.naturalD20() + 10 + 2 + 2 + 1); // DES 10, Precisão 2, HIT 2, nova 1
        assertThat(m.edited()).isTrue();
        assertThat(m.editReason()).isEqualTo("regra da casa");
        assertThat(m.breakdown()).filteredOn(RollPart::edited).hasSize(2);
    }

    @Test
    @DisplayName("override sem motivo, em modo rápido ou sobre dado é 400")
    void testRoll_overrideInvalid() {
        List<RollOverride> ov = List.of(new RollOverride("DES", 10));
        assertThatThrownBy(() -> service.testRoll(sheet.getId(), technique(0, false, null, ov, " ", null), player.getId()))
                .isInstanceOf(InvalidRollException.class);
        assertThatThrownBy(() -> service.testRoll(sheet.getId(), technique(0, false, null, ov, null, null), player.getId()))
                .isInstanceOf(InvalidRollException.class);
        assertThatThrownBy(() -> service.testRoll(sheet.getId(), technique(0, false, null,
                List.of(new RollOverride("d20", 20)), "x", null), player.getId()))
                .isInstanceOf(InvalidRollException.class);
        RollTestRequest quick = new RollTestRequest(RollSourceType.TECHNIQUE, technique.getId(), null, null, null,
                RollMode.QUICK, null, null, null, null, 0, null, false, false, null, null, ov, "x");
        assertThatThrownBy(() -> service.testRoll(sheet.getId(), quick, player.getId()))
                .isInstanceOf(InvalidRollException.class);
        verify(rollRepository, never()).save(any());
    }

    @Test
    @DisplayName("GM_PRIVATE exige Mestre (403); Mestre pode")
    void testRoll_privateRequiresMaster() {
        assertThatThrownBy(() -> service.testRoll(sheet.getId(),
                technique(0, false, null, null, null, RollChannel.GM_PRIVATE), player.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.roll(sheet.getId(),
                new RollRequest("1d20", "x", RollChannel.GM_PRIVATE, null, null), player.getId()))
                .isInstanceOf(AccessDeniedException.class);

        RollMessage m = service.testRoll(sheet.getId(),
                technique(0, false, null, null, null, RollChannel.GM_PRIVATE), master.getId());
        assertThat(m.channel()).isEqualTo(RollChannel.GM_PRIVATE);
    }

    @Test
    @DisplayName("outro jogador não rola nem vê perfis da ficha alheia (403)")
    void testRoll_otherDenied() {
        assertThatThrownBy(() -> service.testRoll(sheet.getId(), technique(0, false, null, null, null, null), other.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.profiles(sheet.getId(), other.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("rolagem livre existente passa a publicar RollMessage")
    void freeRoll_publishesMessage() {
        service.roll(sheet.getId(), new RollRequest("1d20+1", "percepção"), player.getId());

        ArgumentCaptor<RollMessage> cap = ArgumentCaptor.forClass(RollMessage.class);
        verify(events).publishEvent(cap.capture());
        assertThat(cap.getValue().campaignId()).isEqualTo(campaign.getId());
        assertThat(cap.getValue().mode()).isEqualTo(RollMode.FREE);
        assertThat(cap.getValue().actorName()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("rolagem livre do Mestre sem ficha: só Mestre (403); publica no canal")
    void campaignRoll() {
        assertThatThrownBy(() -> service.campaignRoll(campaign.getId(),
                new CampaignRollRequest("2d6", "emboscada", RollChannel.PUBLIC, null), player.getId()))
                .isInstanceOf(AccessDeniedException.class);

        RollMessage m = service.campaignRoll(campaign.getId(),
                new CampaignRollRequest("2d6", "emboscada", RollChannel.GM_PRIVATE, null), master.getId());

        assertThat(m.sheetId()).isNull();
        assertThat(m.channel()).isEqualTo(RollChannel.GM_PRIVATE);
        assertThat(m.actorName()).isEqualTo("Mestre");
        verify(events).publishEvent(any(RollMessage.class));
    }

    private Roll privateRoll(boolean revealed) {
        return Roll.builder().id(UUID.randomUUID()).campaignId(campaign.getId()).actorId(master.getId())
                .channel(RollChannel.GM_PRIVATE).diceResults("[3]").total(3).revealed(revealed).build();
    }

    @Test
    @DisplayName("revelar: só Mestre; privada vira revealed e publica; já revelada não republica")
    void reveal() {
        Roll r = privateRoll(false);
        lenient().when(rollRepository.findById(r.getId())).thenReturn(Optional.of(r));
        lenient().when(userRepository.findAllById(any())).thenReturn(List.of(master));

        assertThatThrownBy(() -> service.reveal(campaign.getId(), r.getId(), player.getId()))
                .isInstanceOf(AccessDeniedException.class);

        RollMessage m = service.reveal(campaign.getId(), r.getId(), master.getId());
        assertThat(m.revealed()).isTrue();
        verify(events).publishEvent(any(RollMessage.class));

        service.reveal(campaign.getId(), r.getId(), master.getId());
        verify(events).publishEvent(any(RollMessage.class)); // continua 1 só
    }

    @Test
    @DisplayName("revelar: rolagem pública ou de outra campanha não serve")
    void reveal_invalid() {
        Roll pub = Roll.builder().id(UUID.randomUUID()).campaignId(campaign.getId()).channel(RollChannel.PUBLIC).build();
        Roll foreign = privateRoll(false);
        foreign = Roll.builder().id(foreign.getId()).campaignId(UUID.randomUUID()).channel(RollChannel.GM_PRIVATE).build();
        Roll f = foreign;
        lenient().when(rollRepository.findById(pub.getId())).thenReturn(Optional.of(pub));
        lenient().when(rollRepository.findById(f.getId())).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.reveal(campaign.getId(), pub.getId(), master.getId()))
                .isInstanceOf(InvalidRollException.class);
        assertThatThrownBy(() -> service.reveal(campaign.getId(), f.getId(), master.getId()))
                .isInstanceOf(RollNotFoundException.class);
    }

    @Test
    @DisplayName("histórico: membro vê o público; privado só o Mestre; não-membro 403")
    void history() {
        lenient().when(rollRepository.findPublic(any(), any(Pageable.class))).thenReturn(List.of(privateRoll(true)));
        lenient().when(rollRepository.findPrivate(any(), any(Pageable.class))).thenReturn(List.of(privateRoll(false)));
        lenient().when(userRepository.findAllById(any())).thenReturn(List.of(master));

        assertThat(service.history(campaign.getId(), null, null, player.getId())).hasSize(1);
        assertThat(service.history(campaign.getId(), RollChannel.GM_PRIVATE, null, master.getId())).hasSize(1);
        assertThatThrownBy(() -> service.history(campaign.getId(), RollChannel.GM_PRIVATE, null, player.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.history(campaign.getId(), RollChannel.PUBLIC, null, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
