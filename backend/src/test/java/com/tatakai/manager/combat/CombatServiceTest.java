package com.tatakai.manager.combat;

import com.tatakai.manager.dto.request.AddConditionRequest;
import com.tatakai.manager.dto.request.CombatActionRequest;
import com.tatakai.manager.dto.request.UpdateParticipantRequest;
import com.tatakai.manager.dto.request.VitalAdjustRequest;
import com.tatakai.manager.dto.response.CombatUpdateMessage;
import com.tatakai.manager.dto.response.EncounterResponse;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.*;
import com.tatakai.manager.repository.*;
import com.tatakai.manager.service.CombatService;
import com.tatakai.manager.service.ConditionCatalog;
import com.tatakai.manager.service.RollService;
import com.tatakai.manager.service.SheetActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CombatService — F6a Motor de combate")
class CombatServiceTest {

    @Mock private EncounterRepository encounters;
    @Mock private EncounterParticipantRepository participants;
    @Mock private CombatConditionRepository conditions;
    @Mock private CombatEventRepository timeline;
    @Mock private CharacterSheetRepository sheets;
    @Mock private CharacterArtRepository arts;
    @Mock private CampaignNpcRepository campaignNpcs;
    @Mock private CampaignMemberRepository members;
    @Mock private RollService rolls;
    @Mock private SheetActivityLogService activityLog;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private PendingAttackRepository pendingAttacks;
    @Mock private CharacterTechniqueRepository techniqueRepo;
    @Mock private MartialArtRepository martialArts;
    @Mock private RivalRepository rivals;
    @Mock private CombatCounterRepository counterRepo;

    private CombatService service;
    private UUID cid;
    private User master, player, other;
    private CharacterSheet sheetA, sheetOther, npcSheet;
    private Encounter enc;
    private final List<EncounterParticipant> pList = new ArrayList<>();
    private final List<CombatCondition> cList = new ArrayList<>();
    private final List<CombatEvent> eventList = new ArrayList<>();

    private static CampaignMember member(Campaign c, User u, Role r) {
        return CampaignMember.builder().id(UUID.randomUUID()).campaign(c).user(u).role(r).build();
    }

    @BeforeEach
    void setUp() {
        Campaign campaign = Campaign.builder().id(UUID.randomUUID()).build();
        cid = campaign.getId();
        master = User.builder().id(UUID.randomUUID()).name("Mestre").build();
        player = User.builder().id(UUID.randomUUID()).name("Jogador").build();
        other = User.builder().id(UUID.randomUUID()).name("Outro").build();
        lenient().when(members.findByCampaignIdAndUserId(cid, master.getId())).thenReturn(Optional.of(member(campaign, master, Role.MASTER)));
        lenient().when(members.findByCampaignIdAndUserId(cid, player.getId())).thenReturn(Optional.of(member(campaign, player, Role.PLAYER)));
        lenient().when(members.findByCampaignIdAndUserId(cid, other.getId())).thenReturn(Optional.of(member(campaign, other, Role.PLAYER)));
        lenient().when(members.existsByCampaignIdAndUserIdAndRole(cid, master.getId(), Role.MASTER)).thenReturn(true);

        sheetA = CharacterSheet.builder().id(UUID.randomUUID()).name("Aiko").buildStatus(BuildStatus.COMPLETED)
                .campaignMember(member(campaign, player, Role.PLAYER)).dex(30).con(40)
                .currentHp(50).hpMax(50).currentSp(10).spMax(10).currentKp(5).kpMax(5).build();
        sheetOther = CharacterSheet.builder().id(UUID.randomUUID()).name("Bruno").buildStatus(BuildStatus.COMPLETED)
                .campaignMember(member(campaign, other, Role.PLAYER)).dex(10).con(10).build();
        npcSheet = CharacterSheet.builder().id(UUID.randomUUID()).name("Capanga").npcId(UUID.randomUUID())
                .npcOwnerId(master.getId()).dex(20).con(20).currentHp(30).hpMax(30).build();
        for (CharacterSheet s : List.of(sheetA, sheetOther, npcSheet)) {
            lenient().when(sheets.findById(s.getId())).thenReturn(Optional.of(s));
        }
        lenient().when(sheets.findAllById(any())).thenAnswer(i -> {
            List<UUID> ids = new ArrayList<>();
            ((Iterable<UUID>) i.getArgument(0)).forEach(ids::add);
            return List.of(sheetA, sheetOther, npcSheet).stream().filter(s -> ids.contains(s.getId())).toList();
        });
        lenient().when(arts.findBySheetIdOrderByCreatedAt(any())).thenReturn(List.of());

        enc = Encounter.builder().id(UUID.randomUUID()).campaignId(cid).name("Briga").status(EncounterStatus.SETUP).build();
        lenient().when(encounters.findById(enc.getId())).thenReturn(Optional.of(enc));
        lenient().when(encounters.save(any(Encounter.class))).thenAnswer(i -> {
            Encounter e = i.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
        lenient().when(participants.findByEncounterIdOrderByOrderIndex(any())).thenAnswer(i ->
                pList.stream().sorted(Comparator.comparingInt(EncounterParticipant::getOrderIndex)).toList());
        lenient().when(participants.existsByEncounterIdAndSheetId(any(), any())).thenAnswer(i ->
                pList.stream().anyMatch(p -> p.getSheetId().equals(i.getArgument(1))));
        lenient().when(participants.save(any(EncounterParticipant.class))).thenAnswer(i -> {
            EncounterParticipant p = i.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            pList.add(p);
            return p;
        });
        lenient().doAnswer(i -> pList.remove(i.<EncounterParticipant>getArgument(0))).when(participants).delete(any(EncounterParticipant.class));
        lenient().when(conditions.findByParticipantIdIn(any())).thenAnswer(i -> {
            java.util.Collection<UUID> ids = i.getArgument(0);
            return new ArrayList<>(cList.stream().filter(c -> ids.contains(c.getParticipantId())).toList());
        });
        lenient().when(conditions.save(any(CombatCondition.class))).thenAnswer(i -> {
            CombatCondition c = i.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            if (!cList.contains(c)) cList.add(c);
            return c;
        });
        lenient().doAnswer(i -> cList.remove(i.<CombatCondition>getArgument(0))).when(conditions).delete(any(CombatCondition.class));
        lenient().doAnswer(i -> { cList.removeAll(i.<Iterable<CombatCondition>>getArgument(0) instanceof java.util.Collection<CombatCondition> c ? c : List.of()); return null; })
                .when(conditions).deleteAll(any(Iterable.class));
        lenient().when(timeline.save(any(CombatEvent.class))).thenAnswer(i -> {
            CombatEvent ev = i.getArgument(0);
            eventList.add(ev);
            return ev;
        });
        // iniciativa controlada: o total vem do mapa abaixo
        lenient().when(rolls.rollInternal(any(), anyString(), anyString(), any(), any())).thenAnswer(i -> {
            CharacterSheet s = i.getArgument(0);
            return Roll.builder().total(initiativeOf(s)).build();
        });
        service = new CombatService(encounters, participants, conditions, timeline, sheets, arts, campaignNpcs,
                members, rolls, activityLog, new ConditionCatalog(), publisher, pendingAttacks,
                new com.tatakai.manager.service.VitalService(participants, conditions, timeline, activityLog, rolls,
                        new ConditionCatalog(), org.mockito.Mockito.mock(com.tatakai.manager.service.RollProfileService.class)),
                new com.tatakai.manager.service.MartialService(arts, martialArts, techniqueRepo, rivals, counterRepo,
                        new com.tatakai.manager.service.ArtCounterCatalog(), timeline));
    }

    private int initA = 10, initB = 10;

    private int initiativeOf(CharacterSheet s) {
        return s == sheetA ? initA : s == npcSheet ? initB : 5;
    }

    private EncounterParticipant add(CharacterSheet s) {
        lenient().when(campaignNpcs.existsByCampaignIdAndNpcId(cid, s.getNpcId())).thenReturn(true);
        service.addParticipant(enc.getId(), s.getId(), master.getId());
        return pList.get(pList.size() - 1);
    }

    /** Encontro ativo com A (jogador, DES 30) e B (NPC, DES 20); A age primeiro quando as iniciativas empatam. */
    private EncounterParticipant[] started() {
        EncounterParticipant a = add(sheetA);
        EncounterParticipant b = add(npcSheet);
        service.start(enc.getId(), master.getId());
        return new EncounterParticipant[]{a, b};
    }

    private void cond(EncounterParticipant p, String key, ConditionExpiry ex, Integer turns, Integer intensity) {
        service.addCondition(enc.getId(), p.getId(), new AddConditionRequest(key, ex, turns, intensity, null, null), master.getId());
    }

    private void act(EncounterParticipant p, CombatAction a, Integer sp, UUID by) {
        service.act(enc.getId(), new CombatActionRequest(p.getId(), a, sp, null, null), by);
    }

    // ---------- encontro e participantes ----------

    @Test
    @DisplayName("Criar encontro: Mestre cria em SETUP; segundo não encerrado -> 409; jogador -> 403")
    void create() {
        EncounterResponse r = service.create(cid, "Duelo", master.getId());
        assertThat(r.status()).isEqualTo(EncounterStatus.SETUP);
        assertThat(r.round()).isZero();

        lenient().when(encounters.existsByCampaignIdAndStatusNot(cid, EncounterStatus.ENDED)).thenReturn(true);
        assertThatThrownBy(() -> service.create(cid, "Outro", master.getId())).isInstanceOf(CombatConflictException.class);
        assertThatThrownBy(() -> service.create(cid, "Outro", player.getId())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Participantes: ficha de jogador precisa estar COMPLETED e ser da campanha")
    void addPlayerSheetRules() {
        sheetA.setBuildStatus(BuildStatus.DRAFT);
        assertThatThrownBy(() -> service.addParticipant(enc.getId(), sheetA.getId(), master.getId()))
                .isInstanceOf(InvalidCombatException.class);

        sheetA.setBuildStatus(BuildStatus.COMPLETED);
        sheetA.setCampaignMember(member(Campaign.builder().id(UUID.randomUUID()).build(), player, Role.PLAYER));
        assertThatThrownBy(() -> service.addParticipant(enc.getId(), sheetA.getId(), master.getId()))
                .isInstanceOf(InvalidCombatException.class);
    }

    @Test
    @DisplayName("Participantes: ficha de NPC só se o NPC estiver associado; duplicata -> 409; jogador -> 403")
    void addNpcRules() {
        lenient().when(campaignNpcs.existsByCampaignIdAndNpcId(cid, npcSheet.getNpcId())).thenReturn(false);
        assertThatThrownBy(() -> service.addParticipant(enc.getId(), npcSheet.getId(), master.getId()))
                .isInstanceOf(InvalidCombatException.class);

        add(sheetA);
        assertThat(pList).hasSize(1);
        assertThat(pList.get(0).getName()).isEqualTo("Aiko");
        assertThat(pList.get(0).isNpc()).isFalse();
        assertThatThrownBy(() -> service.addParticipant(enc.getId(), sheetA.getId(), master.getId()))
                .isInstanceOf(CombatConflictException.class);
        assertThatThrownBy(() -> service.addParticipant(enc.getId(), sheetOther.getId(), player.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Participantes: NPC associado entra marcado como npc")
    void addNpc() {
        EncounterParticipant b = add(npcSheet);
        assertThat(b.isNpc()).isTrue();
        assertThat(b.getInitiative()).isNull();
    }

    // ---------- início ----------

    @Test
    @DisplayName("Início: empate de iniciativa desempata por DES; round 1; primeiro turno com 5 PA")
    void startTieBreakByDex() {
        EncounterParticipant[] p = started();
        assertThat(enc.getStatus()).isEqualTo(EncounterStatus.ACTIVE);
        assertThat(enc.getRound()).isEqualTo(1);
        assertThat(enc.getTurnCounter()).isEqualTo(1);
        assertThat(p[0].getOrderIndex()).isZero();
        assertThat(p[1].getOrderIndex()).isEqualTo(1);
        assertThat(p[0].getPaCurrent()).isEqualTo(5);
        assertThat(p[0].getTurnStartedAt()).isEqualTo(1);
        assertThat(eventList).extracting(CombatEvent::getType).contains(CombatEventType.START, CombatEventType.TURN_START);
        EncounterResponse r = service.get(enc.getId(), master.getId());
        assertThat(r.currentParticipantId()).isEqualTo(p[0].getId());
    }

    @Test
    @DisplayName("Início: maior iniciativa age primeiro, mesmo com DES menor")
    void startOrdersByInitiative() {
        initB = 18;
        EncounterParticipant[] p = started();
        assertThat(p[1].getOrderIndex()).isZero();
        assertThat(p[0].getOrderIndex()).isEqualTo(1);
        assertThat(p[1].getPaCurrent()).isEqualTo(5);
        assertThat(p[0].getPaCurrent()).isZero();
    }

    @Test
    @DisplayName("Início: quem já tem iniciativa definida não é rolado de novo; vazio -> 400; só SETUP -> 409")
    void startRules() {
        assertThatThrownBy(() -> service.start(enc.getId(), master.getId())).isInstanceOf(InvalidCombatException.class);
        EncounterParticipant a = add(sheetA);
        a.setInitiative(25);
        service.start(enc.getId(), master.getId());
        verify(rolls, never()).rollInternal(eq(sheetA), anyString(), anyString(), any(), any());
        assertThatThrownBy(() -> service.start(enc.getId(), master.getId())).isInstanceOf(CombatConflictException.class);
    }

    @Test
    @DisplayName("Rerrolar e definir iniciativa: só o Mestre")
    void rerollAndSet() {
        EncounterParticipant a = add(sheetA);
        initA = 17;
        service.rerollInitiative(enc.getId(), a.getId(), master.getId());
        assertThat(a.getInitiative()).isEqualTo(17);
        service.updateParticipant(enc.getId(), a.getId(), new UpdateParticipantRequest(3, null, null, null, null), master.getId());
        assertThat(a.getInitiative()).isEqualTo(3);
        assertThatThrownBy(() -> service.rerollInitiative(enc.getId(), a.getId(), player.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------- PA ----------

    @Test
    @DisplayName("PA: recarga com bônus e perda do turno anterior, que depois zeram")
    void paRechargeWithBonusAndLoss() {
        EncounterParticipant[] p = started();
        p[0].setPaBonusNext(2);
        p[0].setPaLossNext(1);
        service.nextTurn(enc.getId(), master.getId()); // vai para B
        service.nextTurn(enc.getId(), master.getId()); // volta para A
        assertThat(p[0].getPaCurrent()).isEqualTo(6);
        assertThat(p[0].getPaBonusNext()).isZero();
        assertThat(p[0].getPaLossNext()).isZero();
        assertThat(enc.getRound()).isEqualTo(2);
    }

    @Test
    @DisplayName("PA: perda maior que 5 deixa o turno com 0 PA")
    void paFloor() {
        EncounterParticipant[] p = started();
        p[1].setPaLossNext(9);
        service.nextTurn(enc.getId(), master.getId());
        assertThat(p[1].getPaCurrent()).isZero();
    }

    @Test
    @DisplayName("Mestre ajusta PA e reordena sem perder o participante atual")
    void masterAdjusts() {
        EncounterParticipant[] p = started();
        service.updateParticipant(enc.getId(), p[1].getId(), new UpdateParticipantRequest(null, 2, 1, 0, 0), master.getId());
        assertThat(p[1].getPaCurrent()).isEqualTo(2);
        assertThat(p[1].getPaBonusNext()).isEqualTo(1);
        assertThat(p[1].getOrderIndex()).isZero();
        assertThat(p[0].getOrderIndex()).isEqualTo(1);
        assertThat(enc.getCurrentIndex()).isEqualTo(1); // A continua sendo o atual
        assertThat(eventList).extracting(CombatEvent::getType).contains(CombatEventType.GM);
    }

    // ---------- ações ----------

    @Test
    @DisplayName("Ação fora do turno -> 400; jogador agindo por outro -> 403; encontro não ativo -> 409")
    void actValidation() {
        EncounterParticipant a = add(sheetA);
        assertThatThrownBy(() -> act(a, CombatAction.MINOR, null, master.getId())).isInstanceOf(CombatConflictException.class);
        EncounterParticipant b = add(npcSheet);
        service.start(enc.getId(), master.getId());
        EncounterParticipant[] p = {a, b};
        assertThatThrownBy(() -> act(p[1], CombatAction.MINOR, null, master.getId())).isInstanceOf(InvalidCombatException.class);
        assertThatThrownBy(() -> act(p[0], CombatAction.MINOR, null, other.getId())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Ação maior: debita PA e SP na ficha, registra log RESOURCE e evento ACTION")
    void majorAction() {
        EncounterParticipant[] p = started();
        service.act(enc.getId(), new CombatActionRequest(p[0].getId(), CombatAction.MAJOR, 2, false, "Soco Duplo"), player.getId());
        assertThat(p[0].getPaCurrent()).isEqualTo(2);
        assertThat(sheetA.getCurrentSp()).isEqualTo(8);
        verify(activityLog).record(eq(sheetA), eq(player.getId()), eq(ActorRole.PLAYER), eq(ActivityCategory.RESOURCE),
                anyString(), eq("currentSp"), eq(10), eq(8), any(), anyString());
        assertThat(eventList.get(eventList.size() - 1).getType()).isEqualTo(CombatEventType.ACTION);
        assertThat(eventList.get(eventList.size() - 1).getText()).contains("Soco Duplo").contains("3 PA").contains("2 SP");
    }

    @Test
    @DisplayName("Ação maior lenta custa 4 PA; PA ou SP insuficientes -> 400 sem debitar nada")
    void insufficientResources() {
        EncounterParticipant[] p = started();
        service.act(enc.getId(), new CombatActionRequest(p[0].getId(), CombatAction.MAJOR, null, true, null), master.getId());
        assertThat(p[0].getPaCurrent()).isEqualTo(1);

        assertThatThrownBy(() -> act(p[0], CombatAction.PREPARED, null, master.getId()))
                .isInstanceOf(InvalidCombatException.class).hasMessageContaining("PA insuficiente");
        p[0].setPaCurrent(5);
        sheetA.setCurrentSp(1);
        assertThatThrownBy(() -> act(p[0], CombatAction.MAJOR, 3, master.getId()))
                .isInstanceOf(InvalidCombatException.class).hasMessageContaining("SP insuficiente");
        assertThat(p[0].getPaCurrent()).isEqualTo(5);
        assertThat(sheetA.getCurrentSp()).isEqualTo(1);
    }

    @Test
    @DisplayName("Recuperar Fôlego: 4 PA, recupera 2 + modQ(CON) SP limitado ao máximo")
    void breathe() {
        EncounterParticipant[] p = started();
        sheetA.setCurrentSp(2);
        act(p[0], CombatAction.BREATHE, null, player.getId());
        assertThat(p[0].getPaCurrent()).isEqualTo(1);
        assertThat(sheetA.getCurrentSp()).isEqualTo(8);

        p[0].setPaCurrent(5);
        act(p[0], CombatAction.BREATHE, null, player.getId());
        assertThat(sheetA.getCurrentSp()).isEqualTo(10); // limitado ao máximo
    }

    @Test
    @DisplayName("Mover: 1 PA, distância cai pela metade a cada deslocamento no turno")
    void move() {
        EncounterParticipant[] p = started();
        act(p[0], CombatAction.MOVE, null, player.getId());
        act(p[0], CombatAction.MOVE, null, player.getId());
        assertThat(p[0].getMovesThisTurn()).isEqualTo(2);
        assertThat(p[0].getPaCurrent()).isEqualTo(3);
        List<String> moves = eventList.stream().filter(e -> e.getType() == CombatEventType.ACTION).map(CombatEvent::getText).toList();
        assertThat(moves.get(0)).contains("Moveu 7 m");
        assertThat(moves.get(1)).contains("Moveu 3,5 m");
    }

    @Test
    @DisplayName("Levantar-se: 2 PA e remove Derrubado; sem a condição -> 400")
    void standUp() {
        EncounterParticipant[] p = started();
        assertThatThrownBy(() -> act(p[0], CombatAction.STAND_UP, null, player.getId())).isInstanceOf(InvalidCombatException.class);
        cond(p[0], "DERRUBADO", null, null, null);
        assertThat(cList).hasSize(1);
        act(p[0], CombatAction.STAND_UP, null, player.getId());
        assertThat(cList).isEmpty();
        assertThat(p[0].getPaCurrent()).isEqualTo(3);
    }

    // ---------- F6d: guarda, base ativa e contadores ----------

    private void guardAct(EncounterParticipant p, Guard g, UUID by) {
        service.act(enc.getId(), new CombatActionRequest(p.getId(), CombatAction.CHANGE_GUARD, null, null, null, null, g, null), by);
    }

    private void baseAct(EncounterParticipant p, UUID baseId, UUID by) {
        service.act(enc.getId(), new CombatActionRequest(p.getId(), CombatAction.CHANGE_BASE, null, null, null, null, null, baseId), by);
    }

    private CharacterTechnique base(CharacterSheet s, TechniqueState st, MoveKind kind) {
        CharacterTechnique b = CharacterTechnique.builder().id(UUID.randomUUID()).sheet(s).kind(kind).state(st).name("Postura").build();
        lenient().when(techniqueRepo.findById(b.getId())).thenReturn(Optional.of(b));
        return b;
    }

    @Test
    @DisplayName("F6d: Trocar guarda custa 1 PA, grava a guarda e reinicia o Timing (guardChangedAt)")
    void changeGuard() {
        EncounterParticipant[] p = started();
        assertThat(p[0].getGuard()).isEqualTo(Guard.NATURAL);
        guardAct(p[0], Guard.INVERTED, player.getId());
        assertThat(p[0].getGuard()).isEqualTo(Guard.INVERTED);
        assertThat(p[0].getGuardChangedAt()).isNotNull();
        assertThat(p[0].getPaCurrent()).isEqualTo(4);
        assertThat(eventList).anyMatch(e -> e.getText().contains("guarda invertida"));
        assertThatThrownBy(() -> guardAct(p[0], null, player.getId())).isInstanceOf(InvalidCombatException.class);
        assertThat(p[0].getPaCurrent()).isEqualTo(4);
    }

    @Test
    @DisplayName("F6d: ambidestro troca a guarda de graça na 1ª vez do turno; a 2ª custa 1 PA; zera no próximo turno")
    void ambidextrousFreeGuard() {
        sheetA.setDominance(Dominance.AMBIDEXTROUS);
        EncounterParticipant[] p = started();
        guardAct(p[0], Guard.INVERTED, player.getId());
        assertThat(p[0].getPaCurrent()).isEqualTo(5);
        guardAct(p[0], Guard.NATURAL, player.getId());
        assertThat(p[0].getPaCurrent()).isEqualTo(4);
        service.nextTurn(enc.getId(), player.getId());
        service.nextTurn(enc.getId(), master.getId());
        assertThat(p[0].getGuardChangesThisTurn()).isZero();
        guardAct(p[0], Guard.INVERTED, player.getId());
        assertThat(p[0].getPaCurrent()).isEqualTo(5);
    }

    @Test
    @DisplayName("F6d: Trocar base valida (BASE ACTIVE da própria ficha), entrar/sair 1 PA, trocar 2 PA")
    void changeBase() {
        EncounterParticipant[] p = started();
        CharacterTechnique ok = base(sheetA, TechniqueState.ACTIVE, MoveKind.BASE);
        baseAct(p[0], ok.getId(), player.getId());
        assertThat(p[0].getActiveBaseId()).isEqualTo(ok.getId());
        assertThat(p[0].getPaCurrent()).isEqualTo(4);
        baseAct(p[0], null, player.getId());
        assertThat(p[0].getActiveBaseId()).isNull();
        assertThat(p[0].getPaCurrent()).isEqualTo(3);
        // §26.4: trocar de uma base para outra custa 2 PA
        CharacterTechnique other2 = base(sheetA, TechniqueState.ACTIVE, MoveKind.BASE);
        baseAct(p[0], ok.getId(), player.getId());
        baseAct(p[0], other2.getId(), player.getId());
        assertThat(p[0].getActiveBaseId()).isEqualTo(other2.getId());
        assertThat(p[0].getPaCurrent()).isZero();
        p[0].setPaCurrent(3);
        // inválidas: inexistente, técnica (não base), inativa, de outra ficha
        for (UUID bad : List.of(UUID.randomUUID(), base(sheetA, TechniqueState.ACTIVE, MoveKind.TECHNIQUE).getId(),
                base(sheetA, TechniqueState.STORED, MoveKind.BASE).getId(),
                base(npcSheet, TechniqueState.ACTIVE, MoveKind.BASE).getId())) {
            assertThatThrownBy(() -> baseAct(p[0], bad, player.getId())).isInstanceOf(InvalidCombatException.class);
        }
        assertThat(p[0].getPaCurrent()).isEqualTo(3);
        // Quebra de Base ativa -> 400 ao assumir
        cond(p[0], "QUEBRA_DE_BASE", null, null, null);
        assertThatThrownBy(() -> baseAct(p[0], ok.getId(), player.getId())).isInstanceOf(InvalidCombatException.class);
    }

    @Test
    @DisplayName("F6d: ficar Derrubado ou com Quebra de Base limpa a base ativa")
    void baseClearedOnDowned() {
        EncounterParticipant[] p = started();
        CharacterTechnique ok = base(sheetA, TechniqueState.ACTIVE, MoveKind.BASE);
        baseAct(p[0], ok.getId(), player.getId());
        cond(p[0], "DERRUBADO", null, null, null);
        assertThat(p[0].getActiveBaseId()).isNull();
        baseAct(p[0], ok.getId(), player.getId());
        assertThat(p[0].getActiveBaseId()).isEqualTo(ok.getId());
        cond(p[0], "QUEBRA_DE_BASE", null, null, null);
        assertThat(p[0].getActiveBaseId()).isNull();
    }

    private final List<CombatCounter> counterRows = new ArrayList<>();

    private void capoeira() {
        CharacterArt ca = CharacterArt.builder().id(UUID.randomUUID()).sheet(sheetA).artId(UUID.randomUUID()).artName("Capoeira")
                .status(CharacterArtStatus.ACTIVE).graduation(Graduation.BEGINNER).createdAt(java.time.Instant.now()).build();
        lenient().when(arts.findBySheetIdOrderByCreatedAt(sheetA.getId())).thenReturn(List.of(ca));
        lenient().when(counterRepo.findByParticipantIdIn(any())).thenAnswer(i -> new ArrayList<>(counterRows));
        lenient().when(counterRepo.save(any(CombatCounter.class))).thenAnswer(i -> {
            CombatCounter c = i.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            if (!counterRows.contains(c)) counterRows.add(c);
            return c;
        });
    }

    @Test
    @DisplayName("F6d: contador criado ao entrar no encontro; Derrubado zera o Ritmo; fim do combate zera")
    void counterLifecycle() {
        capoeira();
        EncounterParticipant[] p = started();
        assertThat(counterRows).hasSize(1);
        CombatCounter ritmo = counterRows.get(0);
        assertThat(ritmo.getCounterKey()).isEqualTo("RITMO");
        ritmo.setValue(3);
        cond(p[0], "DERRUBADO", null, null, null);
        assertThat(ritmo.getValue()).isZero();
        ritmo.setValue(2);
        cond(p[0], "AGARRADO", null, null, null);
        assertThat(ritmo.getValue()).isZero();
        ritmo.setValue(4);
        service.end(enc.getId(), master.getId());
        assertThat(ritmo.getValue()).isZero();
    }

    @Test
    @DisplayName("F6d: PATCH do contador — dono ou Mestre; fora do intervalo 400; outro jogador 403; entra na resposta")
    void patchCounter() {
        capoeira();
        EncounterParticipant[] p = started();
        CombatCounter ritmo = counterRows.get(0);
        EncounterResponse r = service.patchCounter(enc.getId(), ritmo.getId(), 3, player.getId());
        assertThat(ritmo.getValue()).isEqualTo(3);
        EncounterResponse.ParticipantResponse pr = r.participants().stream().filter(x -> x.id().equals(p[0].getId())).findFirst().get();
        assertThat(pr.counters()).hasSize(1);
        assertThat(pr.counters().get(0).value()).isEqualTo(3);
        assertThat(pr.counters().get(0).max()).isEqualTo(5);
        assertThat(eventList).anyMatch(e -> e.getText().contains("Ritmo 0 → 3"));
        service.patchCounter(enc.getId(), ritmo.getId(), 1, master.getId());
        assertThat(ritmo.getValue()).isEqualTo(1);
        assertThatThrownBy(() -> service.patchCounter(enc.getId(), ritmo.getId(), 6, player.getId()))
                .isInstanceOf(InvalidCombatException.class);
        assertThatThrownBy(() -> service.patchCounter(enc.getId(), ritmo.getId(), 2, other.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.patchCounter(enc.getId(), UUID.randomUUID(), 2, master.getId()))
                .isInstanceOf(EncounterNotFoundException.class);
    }

    @Test
    @DisplayName("F6d: resposta traz arte principal, tipo, guarda, pé à frente; arts/bases só para dono e Mestre")
    void participantMartialView() {
        CharacterArt ca = CharacterArt.builder().id(UUID.randomUUID()).sheet(sheetA).artId(UUID.randomUUID()).artName("Boxe")
                .status(CharacterArtStatus.ACTIVE).graduation(Graduation.BEGINNER).createdAt(java.time.Instant.now()).build();
        lenient().when(arts.findBySheetIdOrderByCreatedAt(sheetA.getId())).thenReturn(List.of(ca));
        lenient().when(martialArts.findById(ca.getArtId())).thenReturn(Optional.of(MartialArt.builder().id(ca.getArtId())
                .name("Boxe").martialType("Trocação").build()));
        CharacterTechnique b = base(sheetA, TechniqueState.ACTIVE, MoveKind.BASE);
        lenient().when(techniqueRepo.findBySheetIdOrderByCreatedAt(sheetA.getId())).thenReturn(List.of(b));
        EncounterParticipant[] p = started();
        baseAct(p[0], b.getId(), player.getId());
        EncounterResponse.ParticipantResponse mine = service.get(enc.getId(), player.getId()).participants().stream()
                .filter(x -> x.id().equals(p[0].getId())).findFirst().get();
        assertThat(mine.mainArtName()).isEqualTo("Boxe");
        assertThat(mine.martialType()).isEqualTo(MartialType.STRIKING);
        assertThat(mine.guard()).isEqualTo(Guard.NATURAL);
        assertThat(mine.leadFoot()).isEqualTo(Foot.LEFT);
        assertThat(mine.activeBaseName()).isEqualTo("Postura");
        assertThat(mine.arts()).hasSize(1);
        assertThat(mine.bases()).hasSize(1);
        EncounterResponse.ParticipantResponse seen = service.get(enc.getId(), other.getId()).participants().stream()
                .filter(x -> x.id().equals(p[0].getId())).findFirst().get();
        assertThat(seen.martialType()).isEqualTo(MartialType.STRIKING);
        assertThat(seen.arts()).isNull();
        assertThat(seen.bases()).isNull();
    }

    @Test
    @DisplayName("F6d: arte principal — dono (só esse campo) ou Mestre escolhem entre as ACTIVE; inválida 400; outro 403")
    void setMainArt() {
        CharacterArt ca = CharacterArt.builder().id(UUID.randomUUID()).sheet(sheetA).artId(UUID.randomUUID()).artName("Boxe")
                .status(CharacterArtStatus.ACTIVE).graduation(Graduation.BEGINNER).createdAt(java.time.Instant.now()).build();
        lenient().when(arts.findBySheetIdOrderByCreatedAt(sheetA.getId())).thenReturn(List.of(ca));
        EncounterParticipant[] p = started();
        UpdateParticipantRequest req = new UpdateParticipantRequest(null, null, null, null, null, null, null, null, ca.getId());
        service.updateParticipant(enc.getId(), p[0].getId(), req, player.getId());
        assertThat(p[0].getMainArtId()).isEqualTo(ca.getId());
        assertThatThrownBy(() -> service.updateParticipant(enc.getId(), p[0].getId(),
                new UpdateParticipantRequest(null, null, null, null, null, null, null, null, UUID.randomUUID()), master.getId()))
                .isInstanceOf(InvalidCombatException.class);
        assertThatThrownBy(() -> service.updateParticipant(enc.getId(), p[0].getId(), req, other.getId()))
                .isInstanceOf(AccessDeniedException.class);
        // dono não pode mexer em mais nada
        assertThatThrownBy(() -> service.updateParticipant(enc.getId(), p[0].getId(),
                new UpdateParticipantRequest(null, 9, null, null, null, null, null, null, ca.getId()), player.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------- condições ----------

    @Test
    @DisplayName("Condição: catálogo define a expiração padrão; chave desconhecida -> 400; contador exige turnos")
    void conditionBasics() {
        EncounterParticipant[] p = started();
        cond(p[1], "DESORIENTADO", null, null, null);
        assertThat(cList.get(0).getExpiresAt()).isEqualTo(ConditionExpiry.END_OF_TARGET_TURN);
        assertThat(cList.get(0).getAppliedTurn()).isEqualTo(1);
        assertThatThrownBy(() -> cond(p[1], "NAO_EXISTE", null, null, null)).isInstanceOf(InvalidCombatException.class);
        assertThatThrownBy(() -> cond(p[1], "PARALISADO", null, null, null)).isInstanceOf(InvalidCombatException.class);
        assertThatThrownBy(() -> service.addCondition(enc.getId(), p[1].getId(),
                new AddConditionRequest("CEGO", null, null, null, null, null), player.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Condição repetida renova; com níveis vale o mais forte (não acumula)")
    void conditionStack() {
        EncounterParticipant[] p = started();
        cond(p[1], "DESORIENTADO", null, null, null);
        service.nextTurn(enc.getId(), master.getId()); // turn_counter 2
        cond(p[1], "DESORIENTADO", ConditionExpiry.MANUAL, null, null);
        assertThat(cList).hasSize(1);
        assertThat(cList.get(0).getExpiresAt()).isEqualTo(ConditionExpiry.MANUAL);
        assertThat(cList.get(0).getAppliedTurn()).isEqualTo(2);

        cond(p[1], "ENVENENADO", null, null, 3);
        cond(p[1], "ENVENENADO", null, null, 2);
        assertThat(cList).hasSize(2);
        assertThat(cList.stream().filter(c -> c.getConditionKey().equals("ENVENENADO")).findFirst().orElseThrow().getIntensity())
                .isEqualTo(3); // não acumula: vale o nível mais forte
    }

    @Test
    @DisplayName("Mestre remove condição; condição de outro encontro -> 404")
    void removeCondition() {
        EncounterParticipant[] p = started();
        cond(p[1], "CEGO", null, null, null);
        UUID id = cList.get(0).getId();
        service.removeCondition(enc.getId(), id, master.getId());
        assertThat(cList).isEmpty();
        assertThatThrownBy(() -> service.removeCondition(enc.getId(), UUID.randomUUID(), master.getId()))
                .isInstanceOf(EncounterNotFoundException.class);
    }

    // ---------- expiração ----------

    @Test
    @DisplayName("START_OF_TARGET_TURN expira quando começa o turno do alvo")
    void expireAtTurnStart() {
        EncounterParticipant[] p = started();
        cond(p[1], "VULNERAVEL", null, null, null);
        assertThat(cList).hasSize(1);
        service.nextTurn(enc.getId(), master.getId()); // começa o turno de B
        assertThat(cList).isEmpty();
        assertThat(eventList).extracting(CombatEvent::getType).contains(CombatEventType.CONDITION_EXPIRED);
    }

    @Test
    @DisplayName("END_OF_TARGET_TURN aplicada no próprio turno do alvo sobrevive até o fim do próximo turno")
    void endOfTurnAppliedDuringOwnTurn() {
        EncounterParticipant[] p = started();
        cond(p[0], "DESORIENTADO", null, null, null); // aplicada no turno de A (turno 1)
        service.nextTurn(enc.getId(), master.getId()); // fim do turno de A: applied == started -> fica
        assertThat(cList).hasSize(1);
        service.nextTurn(enc.getId(), master.getId()); // B termina, A começa (turno 3)
        assertThat(cList).hasSize(1);
        service.nextTurn(enc.getId(), master.getId()); // fim do turno 3 de A -> expira
        assertThat(cList).isEmpty();
    }

    @Test
    @DisplayName("END_OF_TARGET_TURN aplicada no turno de outro expira no fim do próximo turno do alvo")
    void endOfTurnAppliedDuringOtherTurn() {
        EncounterParticipant[] p = started();
        service.nextTurn(enc.getId(), master.getId()); // turno de B
        cond(p[0], "DESORIENTADO", null, null, null);  // aplicada no turno de B (turno 2)
        service.nextTurn(enc.getId(), master.getId()); // começa A (turno 3)
        assertThat(cList).hasSize(1);
        service.nextTurn(enc.getId(), master.getId()); // fim do turno de A
        assertThat(cList).isEmpty();
    }

    @Test
    @DisplayName("END_OF_ROUND expira só quando a ordem dá a volta; round soma 1 e registra ROUND_END")
    void endOfRound() {
        EncounterParticipant[] p = started();
        cond(p[1], "CEGO", ConditionExpiry.END_OF_ROUND, null, null);
        service.nextTurn(enc.getId(), master.getId()); // A -> B
        assertThat(cList).hasSize(1);
        assertThat(enc.getRound()).isEqualTo(1);
        service.nextTurn(enc.getId(), master.getId()); // B -> A (volta)
        assertThat(cList).isEmpty();
        assertThat(enc.getRound()).isEqualTo(2);
        assertThat(eventList).extracting(CombatEvent::getType).contains(CombatEventType.ROUND_END);
    }

    @Test
    @DisplayName("COUNTER decrementa ao fim de cada turno do alvo e expira em zero")
    void counter() {
        EncounterParticipant[] p = started();
        cond(p[0], "PARALISADO", null, 2, null);
        service.nextTurn(enc.getId(), master.getId()); // fim do turno de A: 2 -> 1
        assertThat(cList.get(0).getTurnsRemaining()).isEqualTo(1);
        service.nextTurn(enc.getId(), master.getId()); // B -> A
        service.nextTurn(enc.getId(), master.getId()); // fim do turno de A: 1 -> 0, expira
        assertThat(cList).isEmpty();
    }

    @Test
    @DisplayName("Atordoado: perde o turno (PA 0 + TURN_LOST) e a condição some ao fim dele")
    void stunned() {
        EncounterParticipant[] p = started();
        cond(p[1], "ATORDOADO", null, null, null); // aplicada no turno de A
        service.nextTurn(enc.getId(), master.getId()); // começa o turno de B
        assertThat(p[1].getPaCurrent()).isZero();
        assertThat(eventList).extracting(CombatEvent::getType).contains(CombatEventType.TURN_LOST);
        assertThat(cList).hasSize(1);
        service.nextTurn(enc.getId(), master.getId()); // fim do turno perdido
        assertThat(cList).isEmpty();
        assertThat(p[0].getPaCurrent()).isEqualTo(5);
    }

    // ---------- avanço e permissões ----------

    @Test
    @DisplayName("Avançar turno: dono do turno atual pode; outro jogador não; encerrado -> 409")
    void nextTurnPermissions() {
        EncounterParticipant[] p = started();
        assertThatThrownBy(() -> service.nextTurn(enc.getId(), other.getId())).isInstanceOf(AccessDeniedException.class);
        service.nextTurn(enc.getId(), player.getId());
        assertThat(enc.getCurrentIndex()).isEqualTo(1);
        assertThatThrownBy(() -> service.nextTurn(enc.getId(), player.getId())).isInstanceOf(AccessDeniedException.class);
        service.end(enc.getId(), master.getId());
        assertThatThrownBy(() -> service.nextTurn(enc.getId(), master.getId())).isInstanceOf(CombatConflictException.class);
        assertThat(p).hasSize(2);
    }

    @Test
    @DisplayName("Remover participante: o atual não pode ser removido; anterior ajusta o índice")
    void removeParticipant() {
        EncounterParticipant[] p = started();
        assertThatThrownBy(() -> service.removeParticipant(enc.getId(), p[0].getId(), master.getId()))
                .isInstanceOf(CombatConflictException.class);
        service.nextTurn(enc.getId(), master.getId()); // atual = B (índice 1)
        service.removeParticipant(enc.getId(), p[0].getId(), master.getId());
        assertThat(enc.getCurrentIndex()).isZero();
        assertThat(p[1].getOrderIndex()).isZero();
    }

    // ---------- visibilidade e fim ----------

    @Test
    @DisplayName("Recursos: Mestre vê todos; jogador só os seus; NPC só para o Mestre")
    void resourcesVisibility() {
        add(sheetA);
        add(sheetOther);
        add(npcSheet);
        EncounterResponse asMaster = service.get(enc.getId(), master.getId());
        assertThat(asMaster.participants()).allMatch(x -> x.resources() != null);
        assertThat(asMaster.participants().get(0).resources().currentSp()).isEqualTo(10);

        EncounterResponse asPlayer = service.get(enc.getId(), player.getId());
        assertThat(asPlayer.participants().get(0).resources()).isNotNull();
        assertThat(asPlayer.participants().get(0).ownerUserId()).isEqualTo(player.getId());
        assertThat(asPlayer.participants().get(1).resources()).isNull();
        assertThat(asPlayer.participants().get(2).resources()).isNull();
        assertThat(asPlayer.participants().get(2).ownerUserId()).isNull();
    }

    @Test
    @DisplayName("Quem não é da campanha não vê o encontro (403)")
    void outsider() {
        lenient().when(members.findByCampaignIdAndUserId(eq(cid), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(enc.getId(), UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Encerrar: status ENDED, apaga condições não MANUAL (as MANUAL ficam), evento END; repetir -> 409")
    void end() {
        EncounterParticipant[] p = started();
        cond(p[0], "DERRUBADO", null, null, null);
        cond(p[1], "CEGO", null, null, null);
        service.end(enc.getId(), master.getId());
        assertThat(enc.getStatus()).isEqualTo(EncounterStatus.ENDED);
        assertThat(enc.getEndedAt()).isNotNull();
        assertThat(cList).extracting(CombatCondition::getConditionKey).containsExactly("DERRUBADO");
        assertThat(eventList).extracting(CombatEvent::getType).contains(CombatEventType.END);
        assertThatThrownBy(() -> service.end(enc.getId(), master.getId())).isInstanceOf(CombatConflictException.class);
    }

    @Test
    @DisplayName("Toda mutação publica CombatUpdateMessage para o tópico combat")
    void publishesUpdates() {
        started();
        verify(publisher, org.mockito.Mockito.atLeastOnce()).publishEvent(any(CombatUpdateMessage.class));
    }

    @Test
    @DisplayName("F6b: ataque aguardando reação bloqueia next-turn (409)")
    void nextTurnBlockedByPendingAttack() {
        started();
        lenient().when(pendingAttacks.existsByEncounterIdAndStatus(enc.getId(), AttackStatus.AWAITING_REACTIONS)).thenReturn(true);
        assertThatThrownBy(() -> service.nextTurn(enc.getId(), master.getId()))
                .isInstanceOf(CombatConflictException.class).hasMessageContaining("aguardando reação");
    }

    @Test
    @DisplayName("F6b: attackers_this_round zera no início do turno do defensor")
    void attackersResetAtTurnStart() {
        EncounterParticipant[] p = started();
        p[1].setAttackersThisRound(new ArrayList<>(List.of(p[0].getId())));
        service.nextTurn(enc.getId(), master.getId()); // começa o turno de B
        assertThat(p[1].getAttackersThisRound()).isEmpty();
    }

    // ---------- F6c ----------

    private void stubWill(int total) {
        lenient().when(rolls.rollCalc(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), anyString(), any(), any()))
                .thenAnswer(x -> new com.tatakai.manager.dto.response.RollMessage(UUID.randomUUID(), cid, null, null, "x", "y",
                        RollChannel.PUBLIC, RollMode.QUICK, "p", RollSourceType.ATTRIBUTE_TEST, null, "s", "f", "f", 0,
                        List.of(), List.of(), 0, total, 10, false, false, null, false, null, false, java.time.Instant.now(), null));
    }

    private boolean has(EncounterParticipant p, String key) {
        return cList.stream().anyMatch(c -> c.getParticipantId().equals(p.getId()) && c.getConditionKey().equals(key));
    }

    @Test
    @DisplayName("F6c: participante herda classificação padrão do encontro e time (jogador 1, NPC 2); criar aceita classificação")
    void classificationAndTeam() {
        EncounterResponse r = service.create(cid, "Duelo", CombatClassification.LETHAL, master.getId());
        assertThat(r.defaultClassification()).isEqualTo(CombatClassification.LETHAL);
        enc.setDefaultClassification(CombatClassification.LETHAL);
        EncounterParticipant[] p = started();
        assertThat(p[0].getClassification()).isEqualTo(CombatClassification.LETHAL);
        assertThat(p[0].getTeam()).isEqualTo(1);
        assertThat(p[1].getTeam()).isEqualTo(2);
        service.updateParticipant(enc.getId(), p[1].getId(), new UpdateParticipantRequest(null, null, null, null, null,
                CombatClassification.EASY, 3, 4), master.getId());
        assertThat(p[1].getClassification()).isEqualTo(CombatClassification.EASY);
        assertThat(p[1].getTeam()).isEqualTo(3);
        assertThat(p[1].getAdrenaline()).isEqualTo(4);
    }

    @Test
    @DisplayName("F6c: PATCH do encontro troca nome e classificação padrão (só o Mestre)")
    void updateEncounter() {
        service.updateEncounter(enc.getId(), new com.tatakai.manager.dto.request.UpdateEncounterRequest("Novo",
                CombatClassification.LETHAL), master.getId());
        assertThat(enc.getName()).isEqualTo("Novo");
        assertThat(enc.getDefaultClassification()).isEqualTo(CombatClassification.LETHAL);
        assertThatThrownBy(() -> service.updateEncounter(enc.getId(), new com.tatakai.manager.dto.request.UpdateEncounterRequest(
                "x", null), player.getId())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("F6c: Adrenalina +2 a cada início de turno; Fluxo reduz 1 SP de Ação Maior")
    void adrenalineAndFlow() {
        EncounterParticipant[] p = started();
        assertThat(p[0].getAdrenaline()).isEqualTo(2);
        p[0].setAdrenaline(5);
        act(p[0], CombatAction.MAJOR, 2, player.getId());
        assertThat(sheetA.getCurrentSp()).isEqualTo(9);
    }

    @Test
    @DisplayName("F6c: fim da rodada resfria -2 quem não se engajou e zera a flag")
    void coolingAtRoundEnd() {
        EncounterParticipant[] p = started();
        p[0].setAdrenaline(6);
        p[1].setAdrenaline(6);
        p[1].setEngagedThisRound(true);
        service.nextTurn(enc.getId(), master.getId());
        service.nextTurn(enc.getId(), master.getId()); // volta a A: fim da rodada 1
        assertThat(p[0].getAdrenaline()).isEqualTo(6); // 4 + 2 do novo turno
        assertThat(p[1].getAdrenaline()).isEqualTo(8);  // engajado: 6 + 2 do turno... (turno de B ocorreu antes)
        assertThat(p[1].isEngagedThisRound()).isFalse();
    }

    @Test
    @DisplayName("F6c: sangramento no início do turno tira HP; Estancar (3 PA) zera as feridas de si ou de aliado")
    void bleedingAndStanch() {
        EncounterParticipant[] p = started();
        sheetA.setOpenWounds(2);
        act(p[0], CombatAction.STANCH, null, player.getId());
        assertThat(sheetA.getOpenWounds()).isZero();
        assertThat(p[0].getPaCurrent()).isEqualTo(2);

        sheetA.setOpenWounds(2);
        service.nextTurn(enc.getId(), master.getId());
        service.nextTurn(enc.getId(), master.getId());
        assertThat(sheetA.getCurrentHp()).isEqualTo(48);
    }

    @Test
    @DisplayName("F6c: Estancar aliado do mesmo time; adversário (400); Agarrado bloqueia (400)")
    void stanchAlly() {
        EncounterParticipant[] p = started();
        p[1].setTeam(1);
        npcSheet.setOpenWounds(5);
        service.act(enc.getId(), new CombatActionRequest(p[0].getId(), CombatAction.STANCH, null, null, null, p[1].getId()),
                master.getId());
        assertThat(npcSheet.getOpenWounds()).isZero();

        p[1].setTeam(2);
        p[0].setPaCurrent(5);
        assertThatThrownBy(() -> service.act(enc.getId(), new CombatActionRequest(p[0].getId(), CombatAction.STANCH, null,
                null, null, p[1].getId()), master.getId())).isInstanceOf(InvalidCombatException.class);
        cond(p[0], "AGARRADO", null, null, null);
        assertThatThrownBy(() -> act(p[0], CombatAction.STANCH, null, master.getId())).isInstanceOf(InvalidCombatException.class);
    }

    @Test
    @DisplayName("F6c: Colapso bloqueia Recuperar Fôlego; Exausto bloqueia ações com SP; Dissociado bloqueia Ação Menor")
    void blockedActions() {
        EncounterParticipant[] p = started();
        sheetA.setPhysicalFatigue(12);
        assertThatThrownBy(() -> act(p[0], CombatAction.BREATHE, null, player.getId())).isInstanceOf(InvalidCombatException.class);
        sheetA.setPhysicalFatigue(0);
        cond(p[0], "EXAUSTO", null, null, null);
        assertThatThrownBy(() -> act(p[0], CombatAction.MAJOR, 1, player.getId()))
                .isInstanceOf(InvalidCombatException.class).hasMessageContaining("Exausto");
        act(p[0], CombatAction.MINOR, null, player.getId()); // sem SP, permitido
        cond(p[0], "DISSOCIADO", null, null, null);
        assertThatThrownBy(() -> act(p[0], CombatAction.MINOR, null, player.getId())).isInstanceOf(InvalidCombatException.class);
        assertThatThrownBy(() -> act(p[0], CombatAction.MINOR_OFFENSIVE, 0, player.getId())).isInstanceOf(InvalidCombatException.class);
    }

    @Test
    @DisplayName("F6c: Dissociado paga o dobro de SP e vai para o fim da iniciativa na largada")
    void dissociatedSpAndOrder() {
        sheetA.setMentalFatigue(12);
        initA = 20;
        EncounterParticipant[] p = started();
        assertThat(p[0].getOrderIndex()).isEqualTo(1);
        assertThat(has(p[0], "DISSOCIADO")).isTrue();
        service.nextTurn(enc.getId(), master.getId());
        act(p[0], CombatAction.MAJOR, 2, player.getId());
        assertThat(sheetA.getCurrentSp()).isEqualTo(6);
    }

    @Test
    @DisplayName("F6c: Respirar fora do Colapso recupera SP e remove Exausto")
    void breatheRemovesExhausted() {
        EncounterParticipant[] p = started();
        sheetA.setCurrentSp(0);
        p[0].setExhaustionNd(14);
        cond(p[0], "EXAUSTO", null, null, null);
        act(p[0], CombatAction.BREATHE, null, player.getId());
        assertThat(sheetA.getCurrentSp()).isGreaterThan(0);
        assertThat(has(p[0], "EXAUSTO")).isFalse();
    }

    @Test
    @DisplayName("F6c: INCONSCIENTE (SP) com COUNTER acorda no fim do 3º turno do alvo com SP 1; inconsciente perde o turno")
    void unconsciousCounterWakes() {
        stubWill(1);
        EncounterParticipant[] p = started();
        sheetA.setCurrentSp(1);
        service.adjustVital(enc.getId(), p[0].getId(), new VitalAdjustRequest(VitalResource.SP, -1, "teste"), master.getId());
        assertThat(has(p[0], "INCONSCIENTE")).isTrue();
        assertThatThrownBy(() -> act(p[0], CombatAction.MINOR, null, player.getId())).isInstanceOf(InvalidCombatException.class);
        for (int i = 0; i < 6; i++) service.nextTurn(enc.getId(), master.getId()); // 3 turnos de A
        assertThat(has(p[0], "INCONSCIENTE")).isFalse();
        assertThat(sheetA.getCurrentSp()).isEqualTo(1);
    }

    @Test
    @DisplayName("F6c: Desorientado do despertar por PQ dura até o fim do próximo turno do alvo")
    void disorientedAfterKpWake() {
        stubWill(1);
        EncounterParticipant[] p = started();
        sheetA.setCurrentKp(1);
        lenient().when(rolls.rollInternal(any(), eq("1d4"), anyString(), any(), any())).thenReturn(Roll.builder().total(1).build());
        service.adjustVital(enc.getId(), p[0].getId(), new VitalAdjustRequest(VitalResource.KP, -1, "teste"), master.getId());
        assertThat(cList.stream().filter(c -> c.getConditionKey().equals("INCONSCIENTE")).findFirst().orElseThrow().getTurnsRemaining())
                .isEqualTo(3);
        for (int i = 0; i < 6; i++) service.nextTurn(enc.getId(), master.getId());
        assertThat(has(p[0], "INCONSCIENTE")).isFalse();
        assertThat(sheetA.getCurrentKp()).isEqualTo(1);
        assertThat(has(p[0], "DESORIENTADO")).isTrue();
        service.nextTurn(enc.getId(), master.getId());
        service.nextTurn(enc.getId(), master.getId()); // próximo turno de A (começa e termina)
        assertThat(has(p[0], "DESORIENTADO")).isFalse();
    }

    @Test
    @DisplayName("F6c: ajuste manual de HP passa pelos autômatos (REAL: inconsciente); jogador -> 403")
    void vitalsRoute() {
        EncounterParticipant[] p = started();
        service.adjustVital(enc.getId(), p[0].getId(), new VitalAdjustRequest(VitalResource.HP, -999, "queda"), master.getId());
        assertThat(sheetA.getCurrentHp()).isZero();
        assertThat(has(p[0], "INCONSCIENTE")).isTrue();
        assertThat(eventList).extracting(CombatEvent::getType).contains(CombatEventType.GM);
        assertThatThrownBy(() -> service.adjustVital(enc.getId(), p[0].getId(),
                new VitalAdjustRequest(VitalResource.HP, 5, "x"), player.getId())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("F6c: Histeria PATCH só no Ápice (400); dono ou Mestre; outro jogador 403")
    void hysteriaRoute() {
        EncounterParticipant[] p = started();
        var req = new com.tatakai.manager.dto.request.SetHysteriaRequest(Hysteria.FURY);
        assertThatThrownBy(() -> service.setHysteria(enc.getId(), p[0].getId(), req, player.getId()))
                .isInstanceOf(InvalidCombatException.class);
        p[0].setAdrenaline(10);
        service.setHysteria(enc.getId(), p[0].getId(), req, player.getId());
        assertThat(p[0].getHysteria()).isEqualTo(Hysteria.FURY);
        assertThatThrownBy(() -> service.setHysteria(enc.getId(), p[0].getId(), req, other.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("F6c: Relâmpago dá +1 PA no início do turno")
    void lightningPa() {
        EncounterParticipant[] p = started();
        p[0].setAdrenaline(8); // +2 no próximo turno de A = 10
        p[0].setHysteria(Hysteria.LIGHTNING);
        p[0].setEngagedThisRound(true);
        service.nextTurn(enc.getId(), master.getId());
        service.nextTurn(enc.getId(), master.getId());
        assertThat(p[0].getAdrenaline()).isEqualTo(10);
        assertThat(p[0].getHysteria()).isEqualTo(Hysteria.LIGHTNING);
        assertThat(p[0].getPaCurrent()).isEqualTo(6);
    }

    @Test
    @DisplayName("F6c: encerrar zera Adrenalina e Feridas e remove Exausto/Dissociado")
    void endCleanup() {
        EncounterParticipant[] p = started();
        p[0].setAdrenaline(7);
        sheetA.setOpenWounds(3);
        cond(p[0], "EXAUSTO", null, null, null);
        service.end(enc.getId(), master.getId());
        assertThat(p[0].getAdrenaline()).isZero();
        assertThat(sheetA.getOpenWounds()).isZero();
        assertThat(has(p[0], "EXAUSTO")).isFalse();
    }
}
