package com.tatakai.manager.combat;

import com.tatakai.manager.dto.request.CreateAttackRequest;
import com.tatakai.manager.dto.request.LateCounterRequest;
import com.tatakai.manager.dto.response.AttackResponse;
import com.tatakai.manager.dto.response.RollMessage;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.*;
import com.tatakai.manager.repository.*;
import com.tatakai.manager.service.AttackService;
import com.tatakai.manager.service.RollProfileService;
import com.tatakai.manager.service.RollProfileService.Calc;
import com.tatakai.manager.service.RollService;
import com.tatakai.manager.service.SheetActivityLogService;
import com.tatakai.manager.service.rules.RollRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("AttackService — F6b Ataque e reação")
class AttackServiceTest {

    @Mock private EncounterRepository encounters;
    @Mock private EncounterParticipantRepository participants;
    @Mock private CombatConditionRepository conditions;
    @Mock private CombatEventRepository timeline;
    @Mock private PendingAttackRepository attackRepo;
    @Mock private AttackTargetRepository targetRepo;
    @Mock private CharacterSheetRepository sheets;
    @Mock private CharacterTechniqueRepository techniques;
    @Mock private CampaignMemberRepository members;
    @Mock private RollService rolls;
    @Mock private RollProfileService profiles;
    @Mock private SheetActivityLogService activityLog;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private CharacterArtRepository charArts;
    @Mock private MartialArtRepository martialArts;
    @Mock private RivalRepository rivals;
    @Mock private CombatCounterRepository counters;

    private AttackService service;
    private UUID cid;
    private User master, player, other;
    private CharacterSheet sheetA, sheetB, npcSheet;
    private EncounterParticipant pA, pB, pN;
    private Encounter enc;
    private CharacterTechnique tech;
    private final List<CombatCondition> cList = new ArrayList<>();
    private final List<PendingAttack> attackList = new ArrayList<>();
    private final List<AttackTarget> targetList = new ArrayList<>();
    private final Deque<RollMessage> queue = new ArrayDeque<>();
    private final Map<UUID, RollMessage> messages = new HashMap<>();
    private final List<Calc> rollCalcs = new ArrayList<>();
    private final Set<String> modules = new HashSet<>();
    private int techSp = 2;

    private static CampaignMember member(Campaign c, User u, Role r) {
        return CampaignMember.builder().id(UUID.randomUUID()).campaign(c).user(u).role(r).build();
    }

    private RollMessage msg(int total, int natural, Integer damage) {
        RollMessage m = new RollMessage(UUID.randomUUID(), cid, null, null, "x", "y", RollChannel.PUBLIC, RollMode.QUICK,
                "p", RollSourceType.CUSTOM, null, "s", "f", "f", 0, List.of(), List.of(), 0, total, natural,
                natural == 20, natural == 1, damage, false, null, false, Instant.now(), null);
        messages.put(m.id(), m);
        return m;
    }

    private Calc calc(RollSourceType t, String name, int sp) {
        return new Calc(t, null, name, null, null, sp, 20, 0, List.of(), new RollRules.Dice(1, 6), List.of());
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

        sheetA = CharacterSheet.builder().id(UUID.randomUUID()).name("Aiko").campaignMember(member(campaign, player, Role.PLAYER))
                .dex(30).con(40).currentHp(50).hpMax(50).currentSp(10).spMax(10).build();
        sheetB = CharacterSheet.builder().id(UUID.randomUUID()).name("Bruno").campaignMember(member(campaign, other, Role.PLAYER))
                .dex(20).con(20).currentHp(40).hpMax(40).currentSp(5).spMax(5).build();
        npcSheet = CharacterSheet.builder().id(UUID.randomUUID()).name("Capanga").npcId(UUID.randomUUID())
                .npcOwnerId(master.getId()).dex(20).con(20).currentHp(30).hpMax(30).currentSp(5).spMax(5).build();
        List<CharacterSheet> all = List.of(sheetA, sheetB, npcSheet);
        lenient().when(sheets.findAllById(any())).thenAnswer(i -> {
            List<UUID> ids = new ArrayList<>();
            ((Iterable<UUID>) i.getArgument(0)).forEach(ids::add);
            return all.stream().filter(s -> ids.contains(s.getId())).toList();
        });

        enc = Encounter.builder().id(UUID.randomUUID()).campaignId(cid).name("Briga").status(EncounterStatus.ACTIVE)
                .round(1).turnCounter(1).currentIndex(0).build();
        lenient().when(encounters.findById(enc.getId())).thenReturn(Optional.of(enc));
        pA = participant(sheetA, 0, false);
        pB = participant(sheetB, 1, false);
        pN = participant(npcSheet, 2, true);
        List<EncounterParticipant> ps = List.of(pA, pB, pN);
        lenient().when(participants.findByEncounterIdOrderByOrderIndex(any())).thenReturn(ps);
        lenient().when(conditions.findByParticipantIdIn(any())).thenAnswer(i -> {
            Collection<UUID> ids = i.getArgument(0);
            return cList.stream().filter(c -> ids.contains(c.getParticipantId())).toList();
        });
        lenient().when(conditions.save(any(CombatCondition.class))).thenAnswer(i -> {
            CombatCondition c = i.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            if (!cList.contains(c)) cList.add(c);
            return c;
        });
        lenient().doAnswer(i -> cList.remove(i.<CombatCondition>getArgument(0))).when(conditions).delete(any(CombatCondition.class));
        lenient().when(attackRepo.save(any(PendingAttack.class))).thenAnswer(i -> {
            PendingAttack a = i.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            if (!attackList.contains(a)) attackList.add(a);
            return a;
        });
        lenient().when(attackRepo.findById(any())).thenAnswer(i -> attackList.stream().filter(a -> a.getId().equals(i.getArgument(0))).findFirst());
        lenient().when(attackRepo.findByEncounterIdAndStatusOrderByCreatedAtDesc(any(), any())).thenAnswer(i ->
                attackList.stream().filter(a -> a.getStatus() == i.getArgument(1)).toList());
        lenient().when(attackRepo.findByEncounterIdAndAttackerIdAndStatus(any(), any(), any())).thenAnswer(i ->
                attackList.stream().filter(a -> a.getAttackerId().equals(i.getArgument(1)) && a.getStatus() == i.getArgument(2)).toList());
        lenient().when(targetRepo.save(any(AttackTarget.class))).thenAnswer(i -> {
            AttackTarget t = i.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            if (!targetList.contains(t)) targetList.add(t);
            return t;
        });
        lenient().when(targetRepo.findByAttackIdIn(any())).thenAnswer(i -> {
            Collection<UUID> ids = i.getArgument(0);
            return targetList.stream().filter(t -> ids.contains(t.getAttackId())).toList();
        });
        // perfis
        tech = CharacterTechnique.builder().id(UUID.randomUUID()).sheet(sheetA).kind(MoveKind.TECHNIQUE)
                .state(TechniqueState.ACTIVE).name("Jab").build();
        lenient().when(techniques.findById(tech.getId())).thenReturn(Optional.of(tech));
        lenient().when(profiles.technique(any(), any(), any(), any())).thenAnswer(i -> calc(RollSourceType.TECHNIQUE, "Jab", techSp));
        lenient().when(profiles.basic(any(), any(), any())).thenAnswer(i -> calc(RollSourceType.BASIC_ATTACK, "Soco", 0));
        lenient().when(profiles.hasModule(any(), any(), any(), anyString())).thenAnswer(i -> modules.contains((String) i.getArgument(3)));
        lenient().when(rolls.rollCalc(any(), any(), any(), anyInt(), anyString(), any(), any())).thenAnswer(i -> {
            rollCalcs.add(i.getArgument(2));
            lastAdv.add(i.getArgument(3));
            return queue.poll();
        });
        lenient().when(rolls.message(any())).thenAnswer(i -> messages.get(i.<UUID>getArgument(0)));
    }

    private final List<Integer> lastAdv = new ArrayList<>();
    private com.tatakai.manager.service.MartialService martial;

    private EncounterParticipant participant(CharacterSheet s, int idx, boolean npc) {
        return EncounterParticipant.builder().id(UUID.randomUUID()).encounterId(enc.getId()).sheetId(s.getId())
                .name(s.getName()).npc(npc).orderIndex(idx).paCurrent(5).build();
    }

    private void cond(EncounterParticipant p, String key) {
        cList.add(CombatCondition.builder().id(UUID.randomUUID()).participantId(p.getId()).conditionKey(key)
                .expiresAt(ConditionExpiry.MANUAL).build());
    }

    private CreateAttackRequest basic(AttackActionKind kind, EncounterParticipant... ts) {
        return new CreateAttackRequest(pA.getId(), RollSourceType.BASIC_ATTACK, null, null, BasicAttack.SOCO, kind,
                Arrays.stream(ts).map(EncounterParticipant::getId).toList(), null, null);
    }

    private CreateAttackRequest technique(AttackActionKind kind, EncounterParticipant... ts) {
        return new CreateAttackRequest(pA.getId(), RollSourceType.TECHNIQUE, tech.getId(), null, null, kind,
                Arrays.stream(ts).map(EncounterParticipant::getId).toList(), null, null);
    }

    /** Ataque de A contra o NPC, resposta do Mestre; devolve o alvo resolvido. */
    private AttackResponse.TargetResponse duel(ReactionType rt, RollMessage atk, RollMessage rea) {
        pA.setPaCurrent(5);
        sheetA.setCurrentSp(10);
        queue.add(atk);
        if (rea != null) queue.add(rea);
        AttackResponse r = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId());
        return service.respond(r.id(), r.targets().get(0).id(), rt, master.getId()).targets().get(0);
    }

    @BeforeEach
    void build() {
        martial = new com.tatakai.manager.service.MartialService(charArts, martialArts, techniques, rivals, counters,
                new com.tatakai.manager.service.ArtCounterCatalog(), timeline);
        lenient().when(counters.findByParticipantIdIn(any())).thenAnswer(i -> new ArrayList<>(counterRows));
        lenient().when(counters.save(any(CombatCounter.class))).thenAnswer(i -> {
            CombatCounter c = i.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            if (!counterRows.contains(c)) counterRows.add(c);
            return c;
        });
        service = new AttackService(encounters, participants, conditions, timeline, attackRepo, targetRepo, sheets,
                techniques, members, rolls, profiles, activityLog, publisher,
                new com.tatakai.manager.service.VitalService(participants, conditions, timeline, activityLog, rolls,
                        new com.tatakai.manager.service.ConditionCatalog(), profiles), martial);
    }

    // ---------- declarar ----------

    @Test
    @DisplayName("Declarar MAJOR: reserva 3 PA e o SP do perfil; aguarda reação")
    void declareMajor() {
        AttackResponse r = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId());
        assertThat(r.status()).isEqualTo(AttackStatus.AWAITING_REACTIONS);
        assertThat(r.reservedPa()).isEqualTo(3);
        assertThat(r.reservedSp()).isEqualTo(2);
        assertThat(pA.getPaCurrent()).isEqualTo(2);
        assertThat(sheetA.getCurrentSp()).isEqualTo(8);
        assertThat(r.targets().get(0).canRespond()).isFalse();
    }

    @Test
    @DisplayName("Declarar com limitação Lenta: 4 PA")
    void declareSlow() {
        modules.add("Lenta");
        assertThat(service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId()).reservedPa()).isEqualTo(4);
    }

    @Test
    @DisplayName("MINOR_OFFENSIVE: só com Técnica Rápida ou ataque básico SOCO; senão 400")
    void minorOffensive() {
        assertThatThrownBy(() -> service.declare(enc.getId(), technique(AttackActionKind.MINOR_OFFENSIVE, pN), player.getId()))
                .isInstanceOf(InvalidCombatException.class);
        assertThat(pA.getPaCurrent()).isEqualTo(5);
        modules.add("Técnica Rápida");
        assertThat(service.declare(enc.getId(), technique(AttackActionKind.MINOR_OFFENSIVE, pN), player.getId()).reservedPa()).isEqualTo(2);
        AttackResponse soco = service.declare(enc.getId(), basic(AttackActionKind.MINOR_OFFENSIVE, pB), player.getId());
        assertThat(soco.reservedPa()).isEqualTo(2);
        assertThat(soco.reservedSp()).isEqualTo(1);
    }

    @Test
    @DisplayName("Declarar: PA ou SP insuficiente -> 400 sem debitar")
    void insufficient() {
        pA.setPaCurrent(2);
        assertThatThrownBy(() -> service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId()))
                .isInstanceOf(InvalidCombatException.class).hasMessageContaining("PA");
        pA.setPaCurrent(5);
        sheetA.setCurrentSp(1);
        assertThatThrownBy(() -> service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId()))
                .isInstanceOf(InvalidCombatException.class).hasMessageContaining("SP");
        assertThat(pA.getPaCurrent()).isEqualTo(5);
        assertThat(sheetA.getCurrentSp()).isEqualTo(1);
    }

    @Test
    @DisplayName("Alvo Furtivo ou o próprio atacante -> 400")
    void invalidTargets() {
        cond(pN, "FURTIVO");
        assertThatThrownBy(() -> service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId()))
                .isInstanceOf(InvalidCombatException.class);
        assertThatThrownBy(() -> service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pA), player.getId()))
                .isInstanceOf(InvalidCombatException.class);
    }

    @Test
    @DisplayName("Declarar: fora do turno -> 400; outro jogador -> 403")
    void turnAndOwner() {
        enc.setCurrentIndex(1);
        assertThatThrownBy(() -> service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId()))
                .isInstanceOf(InvalidCombatException.class);
        enc.setCurrentIndex(0);
        assertThatThrownBy(() -> service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), other.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Alvo Vulnerável/Inconsciente: reação NONE automática e resolve na hora")
    void autoNone() {
        cond(pN, "VULNERAVEL");
        queue.add(msg(15, 10, 7));
        AttackResponse r = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId());
        assertThat(r.status()).isEqualTo(AttackStatus.RESOLVED);
        AttackResponse.TargetResponse t = r.targets().get(0);
        assertThat(t.autoResolved()).isTrue();
        assertThat(t.reaction()).isEqualTo(ReactionType.NONE);
        assertThat(t.outcome()).isEqualTo(AttackOutcome.HIT);
        assertThat(t.damage()).isEqualTo(7);
        assertThat(npcSheet.getCurrentHp()).isEqualTo(23);
        assertThat(rollCalcs).hasSize(1); // só o ataque rola
    }

    // ---------- responder / cancelar ----------

    @Test
    @DisplayName("Responder: jogador pelo próprio alvo, Mestre por qualquer um; outros 403")
    void respondPermissions() {
        AttackResponse r = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pB, pN), player.getId());
        UUID tb = r.targets().get(0).id();
        UUID tn = r.targets().get(1).id();
        queue.add(msg(10, 10, 5));
        assertThatThrownBy(() -> service.respond(r.id(), tb, ReactionType.NONE, player.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.respond(r.id(), tn, ReactionType.NONE, other.getId()))
                .isInstanceOf(AccessDeniedException.class);
        service.respond(r.id(), tb, ReactionType.NONE, other.getId());
        assertThatThrownBy(() -> service.respond(r.id(), tb, ReactionType.NONE, other.getId()))
                .isInstanceOf(CombatConflictException.class);
        AttackResponse done = service.respond(r.id(), tn, ReactionType.NONE, master.getId());
        assertThat(done.status()).isEqualTo(AttackStatus.RESOLVED);
        assertThat(sheetB.getCurrentHp()).isEqualTo(35);
    }

    @Test
    @DisplayName("Opções de reação só para quem pode responder; Contra indisponível sem SP")
    void options() {
        sheetB.setCurrentSp(0);
        AttackResponse r = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pB), player.getId());
        assertThat(r.targets().get(0).options()).isNull();
        AttackResponse asB = service.list(enc.getId(), AttackStatus.AWAITING_REACTIONS, other.getId()).get(0);
        var opts = asB.targets().get(0).options();
        assertThat(opts).hasSize(4);
        assertThat(opts.stream().filter(o -> o.reaction() == ReactionType.COUNTER).findFirst().get().available()).isFalse();
        assertThatThrownBy(() -> service.respond(r.id(), r.targets().get(0).id(), ReactionType.COUNTER, other.getId()))
                .isInstanceOf(InvalidCombatException.class);
    }

    @Test
    @DisplayName("Cancelar antes de resposta devolve PA e SP; após resposta -> 409; terceiros -> 403")
    void cancel() {
        AttackResponse r = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pB, pN), player.getId());
        assertThatThrownBy(() -> service.cancel(r.id(), other.getId())).isInstanceOf(AccessDeniedException.class);
        AttackResponse c = service.cancel(r.id(), player.getId());
        assertThat(c.status()).isEqualTo(AttackStatus.CANCELLED);
        assertThat(pA.getPaCurrent()).isEqualTo(5);
        assertThat(sheetA.getCurrentSp()).isEqualTo(10);

        AttackResponse r2 = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pB, pN), player.getId());
        service.respond(r2.id(), r2.targets().get(0).id(), ReactionType.NONE, other.getId());
        assertThatThrownBy(() -> service.cancel(r2.id(), player.getId())).isInstanceOf(CombatConflictException.class);
    }

    // ---------- desfechos ----------

    @Test
    @DisplayName("Falha crítica (1 natural): MISS, defensor não rola")
    void fumble() {
        var t = duel(ReactionType.DEFEND, msg(3, 1, 6), null);
        assertThat(t.outcome()).isEqualTo(AttackOutcome.MISS);
        assertThat(t.damage()).isZero();
        assertThat(rollCalcs).hasSize(1);
        assertThat(npcSheet.getCurrentHp()).isEqualTo(30);
    }

    @Test
    @DisplayName("Sem reação: HIT com dano cheio; Esquiva empata e falha; Esquiva maior evita")
    void noneAndDodge() {
        var t = duel(ReactionType.DODGE, msg(15, 10, 8), msg(15, 10, null));
        assertThat(t.outcome()).isEqualTo(AttackOutcome.HIT);
        assertThat(t.damage()).isEqualTo(8);
        var d = duel(ReactionType.DODGE, msg(15, 10, 8), msg(16, 11, null));
        assertThat(d.outcome()).isEqualTo(AttackOutcome.DODGED);
        assertThat(d.damage()).isZero();
    }

    @Test
    @DisplayName("Defender: faixas 50/75/100 e 25% na falha")
    void defendBands() {
        assertThat(duel(ReactionType.DEFEND, msg(15, 10, 10), msg(16, 10, null)).mitigationPct()).isEqualTo(50);
        assertThat(duel(ReactionType.DEFEND, msg(15, 10, 10), msg(20, 10, null)).mitigationPct()).isEqualTo(75);
        var full = duel(ReactionType.DEFEND, msg(15, 10, 10), msg(25, 10, null));
        assertThat(full.mitigationPct()).isEqualTo(100);
        assertThat(full.damage()).isZero();
        var fail = duel(ReactionType.DEFEND, msg(15, 10, 10), msg(10, 10, null));
        assertThat(fail.outcome()).isEqualTo(AttackOutcome.HIT);
        assertThat(fail.mitigationPct()).isEqualTo(25);
        assertThat(fail.damage()).isEqualTo(7);
    }

    @Test
    @DisplayName("Romper Guarda remove os 25% da defesa falha")
    void breakGuard() {
        modules.add("Romper Guarda");
        var fail = duel(ReactionType.DEFEND, msg(15, 10, 10), msg(10, 10, null));
        assertThat(fail.mitigationPct()).isZero();
        assertThat(fail.damage()).isEqualTo(10);
    }

    @Test
    @DisplayName("Contra-ataque com sucesso: ambos recebem dano; com 1,5x só o atacante; SP do defensor -1")
    void counterDamage() {
        queue.add(msg(10, 10, 8));
        queue.add(msg(11, 10, null));
        queue.add(msg(5, 10, 4)); // SOCO do defensor
        AttackResponse r = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId());
        var t = service.respond(r.id(), r.targets().get(0).id(), ReactionType.COUNTER, master.getId()).targets().get(0);
        assertThat(t.outcome()).isEqualTo(AttackOutcome.COUNTERED);
        assertThat(t.damage()).isEqualTo(8);
        assertThat(t.counterDamage()).isEqualTo(4);
        assertThat(npcSheet.getCurrentHp()).isEqualTo(22);
        assertThat(npcSheet.getCurrentSp()).isEqualTo(4);
        assertThat(sheetA.getCurrentHp()).isEqualTo(46);

        pA.setPaCurrent(5);
        queue.add(msg(10, 10, 8));
        queue.add(msg(15, 10, null));
        queue.add(msg(5, 10, 3));
        AttackResponse r2 = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId());
        var t2 = service.respond(r2.id(), r2.targets().get(0).id(), ReactionType.COUNTER, master.getId()).targets().get(0);
        assertThat(t2.damage()).isZero();
        assertThat(t2.counterDamage()).isEqualTo(3);
    }

    @Test
    @DisplayName("Crítico vs crítico: Defender 50%; Esquiva falha")
    void critClash() {
        var d = duel(ReactionType.DEFEND, msg(25, 20, 12), msg(40, 20, null));
        assertThat(d.outcome()).isEqualTo(AttackOutcome.DEFENDED);
        assertThat(d.mitigationPct()).isEqualTo(50);
        assertThat(d.damage()).isEqualTo(6);
        var e = duel(ReactionType.DODGE, msg(25, 20, 12), msg(40, 20, null));
        assertThat(e.outcome()).isEqualTo(AttackOutcome.HIT);
        assertThat(e.damage()).isEqualTo(12);
    }

    @Test
    @DisplayName("Dano: mínimo 1 quando conecta com mitigação < 100%; HP nunca abaixo de 0")
    void damageFloors() {
        var t = duel(ReactionType.DEFEND, msg(15, 10, 1), msg(20, 10, null)); // 75% de 1
        assertThat(t.damage()).isEqualTo(1);
        npcSheet.setCurrentHp(2);
        pA.setPaCurrent(5);
        duel(ReactionType.NONE, msg(15, 10, 50), null);
        assertThat(npcSheet.getCurrentHp()).isZero();
    }

    // ---------- modificadores da reação ----------

    @Test
    @DisplayName("Luta contra Grupo: -(n-1) na reação, contando o atacante atual")
    void groupPenalty() {
        pN.setAttackersThisRound(new ArrayList<>(List.of(pB.getId())));
        duel(ReactionType.DEFEND, msg(15, 10, 5), msg(15, 10, null));
        Calc reaction = rollCalcs.get(1);
        assertThat(reaction.hit().stream().filter(p -> p.source().equals("Luta contra Grupo")).findFirst().get().value()).isEqualTo(-1);
        assertThat(pN.getAttackersThisRound()).containsExactlyInAnyOrder(pB.getId(), pA.getId());
    }

    @Test
    @DisplayName("Timing: +1 por uso anterior resolvido da mesma fonte contra o mesmo alvo")
    void timing() {
        duel(ReactionType.NONE, msg(15, 10, 5), null);
        pA.setPaCurrent(5);
        pN.setAttackersThisRound(new ArrayList<>());
        rollCalcs.clear();
        duel(ReactionType.DEFEND, msg(15, 10, 5), msg(15, 10, null));
        Calc reaction = rollCalcs.get(1);
        assertThat(reaction.hit().stream().anyMatch(p -> p.source().equals("Timing") && p.value() == 1)).isTrue();
    }

    @Test
    @DisplayName("Vantagem do atacante: Imobilizado -1, Desorientado -1, alvo Agarrado +1; reação Derrubado -1")
    void advantages() {
        cond(pA, "IMOBILIZADO");
        cond(pA, "DESORIENTADO");
        cond(pN, "AGARRADO");
        cond(pN, "DERRUBADO");
        duel(ReactionType.DODGE, msg(15, 10, 5), msg(15, 10, null));
        assertThat(lastAdv).containsExactly(-1, -1); // ataque: -1-1+1 ; esquiva: derrubado -1
    }

    // ---------- F6c ----------

    private AttackResponse.TargetResponse regionDuel(String region, RollMessage atk) {
        pA.setPaCurrent(5);
        sheetA.setCurrentSp(10);
        queue.add(atk);
        AttackResponse r = service.declare(enc.getId(), new CreateAttackRequest(pA.getId(), RollSourceType.TECHNIQUE,
                tech.getId(), null, null, AttackActionKind.MAJOR, List.of(pN.getId()), region, null), player.getId());
        return service.respond(r.id(), r.targets().get(0).id(), ReactionType.NONE, master.getId()).targets().get(0);
    }

    @Test
    @DisplayName("F6c: HP 0 por ataque em REAL deixa INCONSCIENTE (HP); em LETHAL, Beira da Morte")
    void hpZeroByAttack() {
        npcSheet.setCurrentHp(3);
        duel(ReactionType.NONE, msg(15, 10, 9), null);
        assertThat(cList).extracting(CombatCondition::getConditionKey).containsExactly("INCONSCIENTE");
        cList.clear();
        npcSheet.setCurrentHp(3);
        pN.setClassification(CombatClassification.LETHAL);
        duel(ReactionType.NONE, msg(15, 10, 9), null);
        assertThat(cList).extracting(CombatCondition::getConditionKey).contains("INCONSCIENTE", "BEIRA_DA_MORTE");
    }

    @Test
    @DisplayName("F6c: ataque marca atacante e alvos como engajados")
    void engaged() {
        duel(ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(pA.isEngagedThisRound()).isTrue();
        assertThat(pN.isEngagedThisRound()).isTrue();
        assertThat(pB.isEngagedThisRound()).isFalse();
    }

    @Test
    @DisplayName("F6c: Ápice dá margem de crítico 19; Fluxo não")
    void apexCritMargin() {
        pA.setAdrenaline(10);
        duel(ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(rollCalcs.get(0).critMargin()).isEqualTo(19);
        pA.setAdrenaline(6);
        rollCalcs.clear();
        duel(ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(rollCalcs.get(0).critMargin()).isEqualTo(20);
    }

    @Test
    @DisplayName("F6c: Fúria +4 no acerto e +1d6 de dano (parcela de dado própria); Relâmpago +2")
    void furyParts() {
        pA.setAdrenaline(10);
        pA.setHysteria(Hysteria.FURY);
        duel(ReactionType.NONE, msg(15, 10, 5), null);
        Calc c = rollCalcs.get(0);
        assertThat(c.hit()).anySatisfy(x -> { assertThat(x.source()).isEqualTo("Fúria"); assertThat(x.value()).isEqualTo(4); });
        assertThat(c.damage()).anySatisfy(x -> {
            assertThat(x.source()).isEqualTo("Fúria +1d6");
            assertThat(x.dice()).hasSize(1);
            assertThat(x.value()).isBetween(1, 6);
        });
        pA.setHysteria(Hysteria.LIGHTNING);
        rollCalcs.clear();
        duel(ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(rollCalcs.get(0).hit()).anySatisfy(x -> assertThat(x.value()).isEqualTo(2));
        assertThat(rollCalcs.get(0).damage()).noneMatch(x -> x.source().startsWith("Fúria"));
    }

    @Test
    @DisplayName("F6c: Fluxo -1 SP (técnica e básico, mín. 0); Dissociação ×2")
    void spAdjustments() {
        pA.setAdrenaline(5);
        assertThat(service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId()).reservedSp()).isEqualTo(1);
        pA.setPaCurrent(5);
        assertThat(service.declare(enc.getId(), basic(AttackActionKind.MINOR_OFFENSIVE, pB), player.getId()).reservedSp()).isZero();
        pA.setAdrenaline(0);
        pA.setPaCurrent(5);
        sheetA.setMentalFatigue(12);
        cond(pA, "DISSOCIADO");
        assertThat(service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId()).reservedSp()).isEqualTo(4);
    }

    @Test
    @DisplayName("F6c: Dissociado não faz ataque MINOR_OFFENSIVE (400) e ataca/reage com -1 vantagem; Exausto não paga SP")
    void dissociatedAndExhausted() {
        cond(pA, "DISSOCIADO");
        assertThatThrownBy(() -> service.declare(enc.getId(), basic(AttackActionKind.MINOR_OFFENSIVE, pB), player.getId()))
                .isInstanceOf(InvalidCombatException.class);
        cond(pN, "DISSOCIADO");
        duel(ReactionType.DODGE, msg(15, 10, 5), msg(15, 10, null));
        assertThat(lastAdv).containsExactly(-1, -1);

        cList.clear();
        cond(pA, "EXAUSTO");
        pA.setPaCurrent(5);
        assertThatThrownBy(() -> service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId()))
                .isInstanceOf(InvalidCombatException.class).hasMessageContaining("Exausto");
    }

    @Test
    @DisplayName("F6c: inconsciente não ataca (400)")
    void unconsciousCannotAttack() {
        cond(pA, "INCONSCIENTE");
        assertThatThrownBy(() -> service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId()))
                .isInstanceOf(InvalidCombatException.class);
    }

    @Test
    @DisplayName("F6c: golpe na cabeça tira PQ (1 + Impacto/POWER); outras regiões não; crítico em Queixo ×2")
    void kpLossByRegion() {
        npcSheet.setKpMax(20);
        npcSheet.setCurrentKp(20);
        regionDuel("Tronco", msg(15, 10, 5));
        assertThat(npcSheet.getCurrentKp()).isEqualTo(20);
        regionDuel("Queixo", msg(15, 10, 5));
        assertThat(npcSheet.getCurrentKp()).isEqualTo(19);
        org.mockito.Mockito.lenient().when(profiles.headImpactAndPower(any(), any(), any())).thenReturn(2);
        regionDuel("Têmpora", msg(15, 10, 5));
        assertThat(npcSheet.getCurrentKp()).isEqualTo(16);
        regionDuel("Queixo", msg(25, 20, 5)); // crítico: (1+2)*2 = 6
        assertThat(npcSheet.getCurrentKp()).isEqualTo(10);
    }

    @Test
    @DisplayName("F6c: Muralha reduz a perda de PQ em 1 e dá +4 na reação; esquivado não perde PQ")
    void wallKp() {
        npcSheet.setKpMax(20);
        npcSheet.setCurrentKp(20);
        pN.setAdrenaline(10);
        pN.setHysteria(Hysteria.WALL);
        regionDuel("Queixo", msg(15, 10, 5));
        assertThat(npcSheet.getCurrentKp()).isEqualTo(20);
        pN.setHysteria(Hysteria.FURY);
        pN.setAdrenaline(0);
        pA.setPaCurrent(5);
        queue.add(msg(15, 10, 5));
        queue.add(msg(20, 10, null));
        AttackResponse r = service.declare(enc.getId(), new CreateAttackRequest(pA.getId(), RollSourceType.TECHNIQUE,
                tech.getId(), null, null, AttackActionKind.MAJOR, List.of(pN.getId()), "Queixo", null), player.getId());
        service.respond(r.id(), r.targets().get(0).id(), ReactionType.DODGE, master.getId());
        assertThat(npcSheet.getCurrentKp()).isEqualTo(20);
    }

    @Test
    @DisplayName("F6c: PQ zerado por golpe dispara o teste de Vontade e o desmaio por PQ")
    void kpZeroByHit() {
        npcSheet.setKpMax(6);
        npcSheet.setCurrentKp(1);
        org.mockito.Mockito.lenient().when(rolls.rollInternal(any(), anyString(), anyString(), any(), any()))
                .thenReturn(Roll.builder().total(2).build());
        pA.setPaCurrent(5);
        queue.add(msg(15, 10, 5)); // ataque
        queue.add(msg(5, 10, null)); // Vontade falha
        AttackResponse r = service.declare(enc.getId(), new CreateAttackRequest(pA.getId(), RollSourceType.TECHNIQUE,
                tech.getId(), null, null, AttackActionKind.MAJOR, List.of(pN.getId()), "Nuca", null), player.getId());
        service.respond(r.id(), r.targets().get(0).id(), ReactionType.NONE, master.getId());
        assertThat(cList).extracting(CombatCondition::getConditionKey).contains("INCONSCIENTE");
    }

    @Test
    @DisplayName("F6c/§32: crítico com dado de dano no máximo cria ferimento permanente (Rompimento) e avisa o Mestre")
    void permanentInjuryWarning() {
        RollMessage crit = new RollMessage(UUID.randomUUID(), cid, null, null, "x", "y", RollChannel.PUBLIC, RollMode.QUICK,
                "p", RollSourceType.CUSTOM, null, "s", "f", "f", 0,
                List.of(new RollPart("DAMAGE", "2d6 (Jab)", 12, List.of(6, 6), false)), List.of(), 0, 25, 20, true, false, 14,
                false, null, false, Instant.now(), null);
        messages.put(crit.id(), crit);
        duel(ReactionType.NONE, crit, null);
        verifyWarn();
        // §32: Rompimento criado na ficha — 14 de 30 HP (47%) = Moderado, −7 HP máx (metade do dano)
        assertThat(npcSheet.getInjuries()).singleElement().satisfies(i -> {
            assertThat(i.getType()).isEqualTo(InjuryType.BLUNT);
            assertThat(i.getSeverity()).isEqualTo(InjurySeverity.MODERATE);
            assertThat(i.getHpMaxReduction()).isEqualTo(7);
            assertThat(i.getLimiterReduction()).isEqualTo(10);
            assertThat(i.getAffectedAttribute()).isNull(); // não localizado
        });
    }

    // ---------- F6d: interação marcial, guarda, Calejamento, contadores, Contra-Ataque Tardio ----------

    private final Map<UUID, List<CharacterArt>> artsOf = new HashMap<>();
    private final List<CombatCounter> counterRows = new ArrayList<>();

    private CharacterArt art(CharacterSheet s, String name, String type, Graduation g, List<String> atk, List<String> rea) {
        UUID id = UUID.randomUUID();
        CharacterArt ca = CharacterArt.builder().id(UUID.randomUUID()).sheet(s).artId(id).artName(name).graduation(g)
                .status(CharacterArtStatus.ACTIVE).createdAt(Instant.now().minusSeconds(100 - artsOf.size())).build();
        lenient().when(martialArts.findById(id)).thenReturn(Optional.of(MartialArt.builder().id(id).name(name)
                .martialType(type).attackSpecializations(atk).reactionSpecializations(rea).build()));
        artsOf.computeIfAbsent(s.getId(), k -> new ArrayList<>()).add(ca);
        lenient().when(charArts.findBySheetIdOrderByCreatedAt(s.getId())).thenAnswer(i -> artsOf.get(s.getId()));
        return ca;
    }

    private void stubBasicWithDes() {
        lenient().when(profiles.basic(any(), any(), any())).thenAnswer(i -> new Calc(RollSourceType.BASIC_ATTACK, null, "Soco",
                null, BasicAttack.SOCO, 0, 20, 0, new ArrayList<>(List.of(new RollPart("HIT", "DES", 3, null, false))),
                new RollRules.Dice(1, 3), List.of()));
    }

    private CreateAttackRequest soco(StrikeSide side) {
        return new CreateAttackRequest(pA.getId(), RollSourceType.BASIC_ATTACK, null, null, BasicAttack.SOCO,
                AttackActionKind.MAJOR, List.of(pN.getId()), null, null, side);
    }

    /** Declara contra o NPC e o Mestre reage. */
    private AttackResponse fight(CreateAttackRequest req, ReactionType rt, RollMessage atk, RollMessage rea) {
        pA.setPaCurrent(5);
        sheetA.setCurrentSp(10);
        queue.add(atk);
        if (rea != null) queue.add(rea);
        AttackResponse r = service.declare(enc.getId(), req, player.getId());
        return service.respond(r.id(), r.targets().get(0).id(), rt, master.getId());
    }

    private boolean hasPart(Calc c, String source) {
        return c.hit().stream().anyMatch(p -> p.source().equals(source));
    }

    private RollPart part(Calc c, String source) {
        return c.hit().stream().filter(p -> p.source().equals(source)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("F6d: atacante vence a interação -> +1 vantagem no acerto e parcela nomeada")
    void interactionAttackerWins() {
        art(sheetA, "Boxe", "Trocação", Graduation.BEGINNER, List.of(), List.of());
        art(npcSheet, "Judô", "Derrubada", Graduation.BEGINNER, List.of(), List.of());
        fight(soco(null), ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(rollCalcs.get(0).advantage()).isEqualTo(1);
        assertThat(hasPart(rollCalcs.get(0), "Interação: Trocação > Derrubada")).isTrue();
    }

    @Test
    @DisplayName("F6d: defensor vence a interação -> +1 vantagem na reação dele (atacante sem vantagem)")
    void interactionDefenderWins() {
        art(sheetA, "Judô", "Derrubada", Graduation.BEGINNER, List.of(), List.of());
        art(npcSheet, "Boxe", "Trocação", Graduation.BEGINNER, List.of(), List.of());
        fight(soco(null), ReactionType.DODGE, msg(15, 10, 5), msg(10, 10, null));
        assertThat(rollCalcs.get(0).advantage()).isZero();
        assertThat(lastAdv.get(1)).isEqualTo(1);
        assertThat(hasPart(rollCalcs.get(1), "Interação: Trocação > Derrubada")).isTrue();
    }

    @Test
    @DisplayName("F6d: GENERAL vence todos; GENERAL x GENERAL = nada")
    void interactionGeneral() {
        art(sheetA, "MMA", "Combate Desarmado - Geral", Graduation.BEGINNER, List.of(), List.of());
        art(npcSheet, "Boxe", "Trocação", Graduation.BEGINNER, List.of(), List.of());
        fight(soco(null), ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(rollCalcs.get(0).advantage()).isEqualTo(1);
        artsOf.get(npcSheet.getId()).clear();
        art(npcSheet, "MMA", "Combate Desarmado - Geral", Graduation.BEGINNER, List.of(), List.of());
        rollCalcs.clear();
        fight(soco(null), ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(rollCalcs.get(0).advantage()).isZero();
    }

    @Test
    @DisplayName("F6d: Coringa na base ativa do perdedor anula a vantagem do atacante")
    void interactionJoker() {
        art(sheetA, "Boxe", "Trocação", Graduation.BEGINNER, List.of(), List.of());
        art(npcSheet, "Judô", "Derrubada", Graduation.BEGINNER, List.of(), List.of());
        CharacterTechnique base = CharacterTechnique.builder().id(UUID.randomUUID()).sheet(npcSheet).kind(MoveKind.BASE)
                .state(TechniqueState.ACTIVE).name("Postura").bonuses(List.of(MasteryBonus.JOKER)).build();
        lenient().when(techniques.findById(base.getId())).thenReturn(Optional.of(base));
        pN.setActiveBaseId(base.getId());
        fight(soco(null), ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(rollCalcs.get(0).advantage()).isZero();
        assertThat(hasPart(rollCalcs.get(0), "Interação anulada por Coringa")).isTrue();
    }

    @Test
    @DisplayName("F6d: tipo efetivo da técnica vem da arte dela, não da arte principal; arte principal escolhida vale")
    void effectiveTypeAndMainArt() {
        CharacterArt boxe = art(sheetA, "Boxe", "Trocação", Graduation.INTERMEDIATE, List.of(), List.of());
        CharacterArt judo = art(sheetA, "Judô", "Derrubada", Graduation.BEGINNER, List.of(), List.of());
        art(npcSheet, "Capoeira", "Raspagem", Graduation.BEGINNER, List.of(), List.of());
        // técnica da arte Judô (Derrubada > Raspagem), apesar de a principal (Boxe) ser Trocação (perde p/ Raspagem)
        tech.setCharacterArt(judo);
        fight(new CreateAttackRequest(pA.getId(), RollSourceType.TECHNIQUE, tech.getId(), null, null,
                AttackActionKind.MAJOR, List.of(pN.getId()), null, null), ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(rollCalcs.get(0).advantage()).isEqualTo(1);
        // ataque básico usa a arte principal: padrão = maior graduação (Boxe, Trocação, perde p/ Raspagem)
        rollCalcs.clear();
        stubBasicWithDes();
        fight(soco(null), ReactionType.DODGE, msg(15, 10, 5), msg(10, 10, null));
        assertThat(rollCalcs.get(0).advantage()).isZero();
        assertThat(lastAdv.get(lastAdv.size() - 1)).isEqualTo(1);
        // arte principal escolhida = Judô: básico agora vence a Raspagem
        pA.setMainArtId(judo.getId());
        rollCalcs.clear();
        fight(soco(null), ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(rollCalcs.get(0).advantage()).isEqualTo(1);
        assertThat(boxe.getId()).isNotEqualTo(judo.getId());
    }

    @Test
    @DisplayName("F6d: ataque básico fora da especialização usa metade do mod; dentro (\"Socos\", \"Geral\") não")
    void basicSpecialization() {
        stubBasicWithDes();
        fight(soco(null), ReactionType.NONE, msg(15, 10, 5), null); // sem arte -> metade
        assertThat(part(rollCalcs.get(0), "Fora da especialização: DES/2").value()).isEqualTo(1);
        rollCalcs.clear();
        CharacterArt a = art(sheetA, "Karatê", "Trocação", Graduation.BEGINNER, List.of("Chute", "Cotoveladas"), List.of());
        fight(soco(null), ReactionType.NONE, msg(15, 10, 5), null); // arte sem Soco
        assertThat(hasPart(rollCalcs.get(0), "Fora da especialização: DES/2")).isTrue();
        rollCalcs.clear();
        artsOf.get(sheetA.getId()).clear();
        art(sheetA, "Boxe", "Trocação", Graduation.BEGINNER, List.of("Socos"), List.of());
        fight(soco(null), ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(part(rollCalcs.get(0), "DES").value()).isEqualTo(3);
        rollCalcs.clear();
        artsOf.get(sheetA.getId()).clear();
        art(sheetA, "MMA", "Combate Desarmado - Geral", Graduation.BEGINNER, List.of("Geral - Ataques Básicos"), List.of());
        fight(soco(null), ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(part(rollCalcs.get(0), "DES").value()).isEqualTo(3);
        assertThat(a).isNotNull();
    }

    @Test
    @DisplayName("F6d: técnica de uma arte do personagem conta como especializada (sem metade)")
    void techniqueNotHalved() {
        fight(new CreateAttackRequest(pA.getId(), RollSourceType.TECHNIQUE, tech.getId(), null, null,
                AttackActionKind.MAJOR, List.of(pN.getId()), null, null), ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(rollCalcs.get(0).hit().stream().noneMatch(p -> p.source().startsWith("Fora da"))).isTrue();
    }

    @Test
    @DisplayName("F6d: reação fora da especialização usa metade do mod; \"Desvio\" especializa a Esquiva")
    void reactionSpecialization() {
        fight(soco(null), ReactionType.DODGE, msg(15, 10, 5), msg(10, 10, null));
        assertThat(rollCalcs.get(1).hit().get(0).source()).isEqualTo("Fora da especialização: DES/2");
        rollCalcs.clear();
        art(npcSheet, "Capoeira", "Raspagem", Graduation.BEGINNER, List.of(), List.of("Desvio", "Contra-ataque"));
        fight(soco(null), ReactionType.DODGE, msg(15, 10, 5), msg(10, 10, null));
        assertThat(rollCalcs.get(1).hit().get(0).source()).isEqualTo("DES");
        rollCalcs.clear();
        fight(soco(null), ReactionType.DEFEND, msg(15, 10, 5), msg(10, 10, null)); // Defesa não especializada
        assertThat(rollCalcs.get(1).hit().get(0).source()).isEqualTo("Fora da especialização: CON/2");
    }

    @Test
    @DisplayName("F6d: guarda Natural/Invertida, lado do golpe, ambidestro e Derrubado")
    void guardParts() {
        stubBasicWithDes();
        art(sheetA, "Boxe", "Trocação", Graduation.BEGINNER, List.of("Socos"), List.of());
        fight(soco(StrikeSide.FRONT), ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(part(rollCalcs.get(0), "Guarda: frente +1").value()).isEqualTo(1);
        // trás na Natural: +1d2 de dano, sem mudança no acerto
        rollCalcs.clear();
        fight(soco(StrikeSide.BACK), ReactionType.NONE, msg(15, 10, 5), null);
        RollPart d = rollCalcs.get(0).damage().stream().filter(p -> p.source().equals("Guarda: trás +1d2")).findFirst().orElseThrow();
        assertThat(d.value()).isBetween(1, 2);
        assertThat(d.dice()).hasSize(1);
        assertThat(hasPart(rollCalcs.get(0), "Guarda invertida: trás −1")).isFalse();
        // trás na Invertida: −1 no acerto
        rollCalcs.clear();
        pA.setGuard(Guard.INVERTED);
        fight(soco(StrikeSide.BACK), ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(part(rollCalcs.get(0), "Guarda invertida: trás −1").value()).isEqualTo(-1);
        // ambidestro: sem o −1
        rollCalcs.clear();
        sheetA.setDominance(Dominance.AMBIDEXTROUS);
        fight(soco(StrikeSide.BACK), ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(hasPart(rollCalcs.get(0), "Guarda invertida: trás −1")).isFalse();
        // Derrubado = Frontal: nada
        rollCalcs.clear();
        cond(pA, "DERRUBADO");
        fight(soco(StrikeSide.FRONT), ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(hasPart(rollCalcs.get(0), "Guarda: frente +1")).isFalse();
        assertThat(rollCalcs.get(0).damage().stream().noneMatch(p -> p.source().startsWith("Guarda"))).isTrue();
    }

    @Test
    @DisplayName("F6d: lado do golpe não vale para Cabeçada/Agarrão; vale para técnica quando informado")
    void guardScope() {
        lenient().when(profiles.basic(any(), any(), any())).thenAnswer(i -> new Calc(RollSourceType.BASIC_ATTACK, null, "Cabeçada",
                null, BasicAttack.CABECADA, 0, 20, 0, new ArrayList<>(), new RollRules.Dice(1, 4), List.of()));
        fight(new CreateAttackRequest(pA.getId(), RollSourceType.BASIC_ATTACK, null, null, BasicAttack.CABECADA,
                AttackActionKind.MAJOR, List.of(pN.getId()), null, null, StrikeSide.FRONT), ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(hasPart(rollCalcs.get(0), "Guarda: frente +1")).isFalse();
        rollCalcs.clear();
        fight(new CreateAttackRequest(pA.getId(), RollSourceType.TECHNIQUE, tech.getId(), null, null,
                AttackActionKind.MAJOR, List.of(pN.getId()), null, null, StrikeSide.FRONT), ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(hasPart(rollCalcs.get(0), "Guarda: frente +1")).isTrue();
    }

    @Test
    @DisplayName("F6d: Vantagem do Incomum (+1 na reação) e seus cancelamentos")
    void incomum() {
        npcSheet.setDominance(Dominance.LEFT); // NATURAL: pé direito à frente; atacante destro: pé esquerdo
        fight(soco(null), ReactionType.DODGE, msg(15, 10, 5), msg(10, 10, null));
        assertThat(part(rollCalcs.get(1), "Vantagem do Incomum +1").value()).isEqualTo(1);
        // defensor em guarda Frontal: não vale
        rollCalcs.clear();
        pN.setGuard(Guard.FRONTAL);
        fight(soco(null), ReactionType.DODGE, msg(15, 10, 5), msg(10, 10, null));
        assertThat(hasPart(rollCalcs.get(1), "Vantagem do Incomum +1")).isFalse();
        // atacante com Leitura > 0 contra o defensor: não vale
        rollCalcs.clear();
        pN.setGuard(Guard.NATURAL);
        lenient().when(rivals.findBySheetIdOrderByName(sheetA.getId())).thenReturn(List.of(
                Rival.builder().name("Capanga").npcId(npcSheet.getNpcId()).readBonus(1).build()));
        fight(soco(null), ReactionType.DODGE, msg(15, 10, 5), msg(10, 10, null));
        assertThat(hasPart(rollCalcs.get(1), "Vantagem do Incomum +1")).isFalse();
    }

    @Test
    @DisplayName("F6d: Leitura de Oponente do defensor soma o readBonus (por npcId ou nome)")
    void readBonus() {
        lenient().when(rivals.findBySheetIdOrderByName(npcSheet.getId())).thenReturn(List.of(
                Rival.builder().name("Outro nome").npcId(null).readBonus(0).build(),
                Rival.builder().name("aiko").readBonus(2).build()));
        fight(soco(null), ReactionType.DODGE, msg(15, 10, 5), msg(10, 10, null));
        assertThat(part(rollCalcs.get(1), "Leitura de Oponente +2").value()).isEqualTo(2);
    }

    @Test
    @DisplayName("F6d: troca de guarda reinicia o Timing (só ataques resolvidos depois contam)")
    void timingResetByGuardChange() {
        fight(soco(null), ReactionType.NONE, msg(15, 10, 5), null);
        rollCalcs.clear();
        pN.setGuardChangedAt(Instant.now().plusSeconds(60));
        pN.setAttackersThisRound(new ArrayList<>());
        fight(soco(null), ReactionType.DEFEND, msg(15, 10, 5), msg(15, 10, null));
        assertThat(hasPart(rollCalcs.get(1), "Timing")).isFalse();
        // sem troca de guarda: Timing +1 (controle)
        pN.setGuardChangedAt(null);
        pN.setAttackersThisRound(new ArrayList<>());
        rollCalcs.clear();
        fight(soco(null), ReactionType.DEFEND, msg(15, 10, 5), msg(15, 10, null));
        assertThat(part(rollCalcs.get(1), "Timing").value()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("F6d: Calejamento reduz o dano depois da % da reação e antes do mínimo 1")
    void calejamento() {
        lenient().when(profiles.calejo(eq(npcSheet), any())).thenReturn(4);
        // sem reação: 10 - 4 = 6
        assertThat(fight(soco(null), ReactionType.NONE, msg(15, 10, 10), null).targets().get(0).damage()).isEqualTo(6);
        // defesa falha (25%): 20 -> 15 -> 11
        assertThat(fight(soco(null), ReactionType.DEFEND, msg(15, 10, 20), msg(10, 10, null)).targets().get(0).damage())
                .isEqualTo(11);
        // defesa 50%: 20 -> 10 -> 6
        assertThat(fight(soco(null), ReactionType.DEFEND, msg(15, 10, 20), msg(16, 10, null)).targets().get(0).damage())
                .isEqualTo(6);
        // mínimo 1 quando a redução passa do dano
        assertThat(fight(soco(null), ReactionType.NONE, msg(15, 10, 3), null).targets().get(0).damage()).isEqualTo(1);
        // 100% de mitigação segue zerando
        assertThat(fight(soco(null), ReactionType.DEFEND, msg(15, 10, 20), msg(25, 10, null)).targets().get(0).damage()).isZero();
    }

    @Test
    @DisplayName("F6d: ataque usa a base ativa nas Opts do perfil")
    void activeBaseInOpts() {
        CharacterTechnique base = CharacterTechnique.builder().id(UUID.randomUUID()).sheet(sheetA).kind(MoveKind.BASE)
                .state(TechniqueState.ACTIVE).name("Postura").build();
        lenient().when(techniques.findById(base.getId())).thenReturn(Optional.of(base));
        pA.setActiveBaseId(base.getId());
        fight(new CreateAttackRequest(pA.getId(), RollSourceType.TECHNIQUE, tech.getId(), null, null,
                AttackActionKind.MAJOR, List.of(pN.getId()), null, null), ReactionType.NONE, msg(15, 10, 5), null);
        org.mockito.ArgumentCaptor<RollProfileService.Opts> cap = org.mockito.ArgumentCaptor.forClass(RollProfileService.Opts.class);
        org.mockito.Mockito.verify(profiles, org.mockito.Mockito.atLeastOnce()).technique(any(), any(), any(), cap.capture());
        assertThat(cap.getAllValues()).allMatch(o -> base.getId().equals(o.baseId()));
        // base que deixou de ser ACTIVE é ignorada em vez de quebrar o ataque
        base.setState(TechniqueState.STORED);
        fight(new CreateAttackRequest(pA.getId(), RollSourceType.TECHNIQUE, tech.getId(), null, null,
                AttackActionKind.MAJOR, List.of(pN.getId()), null, null), ReactionType.NONE, msg(15, 10, 5), null);
    }

    @Test
    @DisplayName("F6d: ataque conectado/defendido dispara eventos dos contadores (HIT, GOT_HIT, DEFEND_SUCCESS)")
    void counterEvents() {
        art(sheetA, "Capoeira", "Raspagem", Graduation.BEGINNER, List.of(), List.of());
        art(npcSheet, "Karatê", "Trocação", Graduation.BEGINNER, List.of(), List.of());
        // acerto sem reação: A (Capoeira) ganha Ritmo
        fight(soco(null), ReactionType.NONE, msg(15, 10, 5), null);
        assertThat(counterRows).anyMatch(c -> c.getParticipantId().equals(pA.getId()) && c.getCounterKey().equals("RITMO")
                && c.getValue() == 1);
        // defesa 100% do NPC (Karatê): Foco
        fight(soco(null), ReactionType.DEFEND, msg(15, 10, 5), msg(25, 10, null));
        assertThat(counterRows).anyMatch(c -> c.getParticipantId().equals(pN.getId()) && c.getCounterKey().equals("FOCO")
                && c.getValue() == 1);
    }

    // ----- Contra-Ataque Tardio -----

    private AttackResponse dodged(int atkTotal, int atkDamage) {
        return fight(soco(null), ReactionType.DODGE, msg(atkTotal, 10, atkDamage), msg(atkTotal + 5, 10, null));
    }

    private LateCounterRequest lateBasic() {
        return new LateCounterRequest(RollSourceType.BASIC_ATTACK, null, null, BasicAttack.SOCO);
    }

    @Test
    @DisplayName("F6d: Contra-Tardio com sucesso (> total original) dá o dano do perfil no atacante e gasta SP, sem PA")
    void lateCounterSuccess() {
        AttackResponse r = dodged(10, 8);
        assertThat(r.targets().get(0).canLateCounter()).isTrue();
        queue.add(msg(20, 10, 6));
        int pa = pN.getPaCurrent();
        AttackResponse out = service.lateCounter(r.id(), r.targets().get(0).id(), lateBasic(), master.getId());
        var t = out.targets().get(0);
        assertThat(t.lateCounter()).isEqualTo(LateCounter.SUCCESS);
        assertThat(t.lateCounterDamage()).isEqualTo(6);
        assertThat(t.canLateCounter()).isFalse();
        assertThat(sheetA.getCurrentHp()).isEqualTo(44);
        assertThat(npcSheet.getCurrentSp()).isEqualTo(4);
        assertThat(pN.getPaCurrent()).isEqualTo(pa);
    }

    @Test
    @DisplayName("F6d: Contra-Tardio com mitigação por Calejamento do atacante")
    void lateCounterCalejo() {
        lenient().when(profiles.calejo(eq(sheetA), any())).thenReturn(4);
        AttackResponse r = dodged(10, 8);
        queue.add(msg(20, 10, 6));
        service.lateCounter(r.id(), r.targets().get(0).id(), lateBasic(), master.getId());
        assertThat(sheetA.getCurrentHp()).isEqualTo(48); // 6 - 4 = 2
    }

    @Test
    @DisplayName("F6d: Contra-Tardio que falha vira falha crítica: dano integral do ataque original no alvo")
    void lateCounterFail() {
        AttackResponse r = dodged(10, 8);
        assertThat(npcSheet.getCurrentHp()).isEqualTo(30);
        queue.add(msg(5, 10, null));
        AttackResponse out = service.lateCounter(r.id(), r.targets().get(0).id(), lateBasic(), master.getId());
        var t = out.targets().get(0);
        assertThat(t.lateCounter()).isEqualTo(LateCounter.FAIL);
        assertThat(t.lateCounterDamage()).isEqualTo(8);
        assertThat(t.damage()).isEqualTo(8);
        assertThat(t.outcome()).isEqualTo(AttackOutcome.HIT);
        assertThat(npcSheet.getCurrentHp()).isEqualTo(22);
    }

    @Test
    @DisplayName("F6d: falha com Resposta Segura na variação -> SAFE, sem dano extra")
    void lateCounterSafe() {
        CharacterTechnique techN = CharacterTechnique.builder().id(UUID.randomUUID()).sheet(npcSheet).kind(MoveKind.TECHNIQUE)
                .state(TechniqueState.ACTIVE).name("Contra").build();
        lenient().when(techniques.findById(techN.getId())).thenReturn(Optional.of(techN));
        modules.add("Resposta Segura");
        AttackResponse r = dodged(10, 8);
        queue.add(msg(5, 10, null));
        AttackResponse out = service.lateCounter(r.id(), r.targets().get(0).id(),
                new LateCounterRequest(RollSourceType.TECHNIQUE, techN.getId(), null, null), master.getId());
        assertThat(out.targets().get(0).lateCounter()).isEqualTo(LateCounter.SAFE);
        assertThat(npcSheet.getCurrentHp()).isEqualTo(30);
    }

    @Test
    @DisplayName("F6d: Contra-Tardio 1x por alvo; janela fecha no próximo next-turn; exige Defender/Esquiva bem-sucedidos")
    void lateCounterGuards() {
        AttackResponse r = dodged(10, 8);
        UUID tid = r.targets().get(0).id();
        queue.add(msg(20, 10, 6));
        service.lateCounter(r.id(), tid, lateBasic(), master.getId());
        assertThatThrownBy(() -> service.lateCounter(r.id(), tid, lateBasic(), master.getId()))
                .isInstanceOf(CombatConflictException.class);

        AttackResponse r2 = dodged(10, 8);
        enc.setTurnCounter(enc.getTurnCounter() + 1); // next-turn
        assertThatThrownBy(() -> service.lateCounter(r2.id(), r2.targets().get(0).id(), lateBasic(), master.getId()))
                .isInstanceOf(CombatConflictException.class);
        assertThat(service.list(enc.getId(), AttackStatus.RESOLVED, master.getId()).get(0).targets().get(0).canLateCounter()).isFalse();
        enc.setTurnCounter(enc.getTurnCounter() - 1);

        AttackResponse hit = fight(soco(null), ReactionType.NONE, msg(15, 10, 5), null);
        assertThatThrownBy(() -> service.lateCounter(hit.id(), hit.targets().get(0).id(), lateBasic(), master.getId()))
                .isInstanceOf(InvalidCombatException.class);
    }

    @Test
    @DisplayName("F6d: Contra-Tardio — só o dono do alvo ou o Mestre; SP insuficiente -> 400 sem debitar")
    void lateCounterPermissions() {
        AttackResponse r = fight(soco(null), ReactionType.DODGE, msg(10, 10, 8), msg(15, 10, null));
        assertThatThrownBy(() -> service.lateCounter(r.id(), r.targets().get(0).id(), lateBasic(), player.getId()))
                .isInstanceOf(AccessDeniedException.class);
        npcSheet.setCurrentSp(0);
        assertThatThrownBy(() -> service.lateCounter(r.id(), r.targets().get(0).id(), lateBasic(), master.getId()))
                .isInstanceOf(InvalidCombatException.class).hasMessageContaining("SP");
        assertThat(npcSheet.getCurrentSp()).isZero();
    }

    private void verifyWarn() {
        org.mockito.ArgumentCaptor<CombatEvent> cap = org.mockito.ArgumentCaptor.forClass(CombatEvent.class);
        org.mockito.Mockito.verify(timeline, org.mockito.Mockito.atLeastOnce()).save(cap.capture());
        assertThat(cap.getAllValues()).anyMatch(ev -> ev.getType() == CombatEventType.GM
                && ev.getText().toLowerCase().contains("ferimento permanente"));
    }

    // ---------- §30: efeitos de módulo no acerto ----------

    private final List<com.tatakai.manager.entity.AssemblyItem> effectItems = new ArrayList<>();

    private void effect(String module, boolean onDie) {
        com.tatakai.manager.entity.AssemblyItem it = new com.tatakai.manager.entity.AssemblyItem();
        it.setModule(module);
        it.setQty(module.equals("Roubar Tempo") ? 2 : 1);
        it.setKind(com.tatakai.manager.entity.AssemblyItemKind.EFFECT);
        it.setOnDie(onDie);
        effectItems.add(it);
        lenient().when(profiles.effects(any(), any(), any())).thenAnswer(x -> effectItems);
        lenient().when(profiles.attribute(any(), any())).thenAnswer(x -> calc(RollSourceType.ATTRIBUTE_TEST, "Teste", 0));
    }

    private List<String> timelineTexts() {
        org.mockito.ArgumentCaptor<CombatEvent> cap = org.mockito.ArgumentCaptor.forClass(CombatEvent.class);
        org.mockito.Mockito.verify(timeline, org.mockito.Mockito.atLeastOnce()).save(cap.capture());
        return cap.getAllValues().stream().map(CombatEvent::getText).toList();
    }

    @Test
    @DisplayName("§30: Derrubar no acerto — alvo falha na resistência (FOR vs total do ataque): Derrubado + dano de Derrubada")
    void effectTakedown() {
        effect("Derrubar", false);
        queue.add(msg(15, 10, 5));
        queue.add(msg(12, 9, null)); // resistência 12 < 15
        AttackResponse r = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId());
        service.respond(r.id(), r.targets().get(0).id(), ReactionType.NONE, master.getId());
        assertThat(cList).anyMatch(c -> c.getParticipantId().equals(pN.getId()) && c.getConditionKey().equals("DERRUBADO"));
        assertThat(npcSheet.getCurrentHp()).isEqualTo(30 - 5 - 1); // golpe + Derrubada (peso 0 → mín. 1)
    }

    @Test
    @DisplayName("§30: resistência igual ao ataque evita o efeito; Roubar Tempo tira PA no próximo turno")
    void effectResistedAndStealPa() {
        effect("Atordoar", false);
        effect("Roubar Tempo", false);
        queue.add(msg(15, 10, 5));
        queue.add(msg(15, 9, null)); // resiste ao Atordoar (empate)
        queue.add(msg(3, 2, null));  // falha no Roubar Tempo
        AttackResponse r = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId());
        service.respond(r.id(), r.targets().get(0).id(), ReactionType.NONE, master.getId());
        assertThat(cList).noneMatch(c -> c.getConditionKey().equals("ATORDOADO"));
        assertThat(pN.getPaLossNext()).isEqualTo(2);
    }

    @Test
    @DisplayName("§30: efeito no dado só com 17+; crítico aplica sem teste; efeito sem regra vira aviso ao Mestre")
    void effectOnDieCritAndManual() {
        effect("Atordoar", true);
        effect("Marcar", false);
        queue.add(msg(15, 12, 5)); // natural 12: Atordoar no dado não dispara
        AttackResponse r = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId());
        service.respond(r.id(), r.targets().get(0).id(), ReactionType.NONE, master.getId());
        assertThat(cList).noneMatch(c -> c.getConditionKey().equals("ATORDOADO"));
        assertThat(timelineTexts()).anyMatch(t -> t.contains("Marcar") && t.contains("manualmente"));

        pA.setPaCurrent(5);
        sheetA.setCurrentSp(10);
        int before = rollCalcs.size();
        queue.add(msg(25, 20, 5)); // crítico: sem teste de resistência
        r = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId());
        service.respond(r.id(), r.targets().get(0).id(), ReactionType.NONE, master.getId());
        assertThat(cList).anyMatch(c -> c.getParticipantId().equals(pN.getId()) && c.getConditionKey().equals("ATORDOADO"));
        assertThat(rollCalcs.size() - before).isEqualTo(1); // só o ataque
    }

    @Test
    @DisplayName("Vulnerável não reage, mas o ataque precisa de total ≥ 10: 9 erra, 10 acerta")
    void vulnerableNeedsNd10() {
        cond(pN, "VULNERAVEL");
        queue.add(msg(9, 8, 5));
        AttackResponse r = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId());
        assertThat(r.status()).isEqualTo(AttackStatus.RESOLVED); // sem reação: resolve na hora
        assertThat(r.targets().get(0).outcome()).isEqualTo(AttackOutcome.MISS);
        assertThat(npcSheet.getCurrentHp()).isEqualTo(30);

        pA.setPaCurrent(5);
        sheetA.setCurrentSp(10);
        queue.add(msg(10, 9, 5));
        r = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId());
        assertThat(r.targets().get(0).outcome()).isEqualTo(AttackOutcome.HIT);
        assertThat(npcSheet.getCurrentHp()).isEqualTo(25);
    }

    @Test
    @DisplayName("Atordoado reage (sem Contra-ataque) e com desvantagem")
    void stunnedReactsWithDisadvantage() {
        cond(pN, "ATORDOADO");
        AttackResponse r = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), master.getId());
        assertThat(r.status()).isEqualTo(AttackStatus.AWAITING_REACTIONS);
        AttackResponse.TargetResponse t = r.targets().get(0);
        assertThat(t.options()).filteredOn(o -> o.reaction() == ReactionType.COUNTER)
                .singleElement().satisfies(o -> assertThat(o.available()).isFalse());
        assertThatThrownBy(() -> service.respond(r.id(), t.id(), ReactionType.COUNTER, master.getId()))
                .isInstanceOf(InvalidCombatException.class);
        queue.add(msg(15, 10, 7));
        queue.add(msg(18, 15, null));
        service.respond(r.id(), t.id(), ReactionType.DODGE, master.getId());
        assertThat(lastAdv.get(lastAdv.size() - 1)).isEqualTo(-1);
    }

    @Test
    @DisplayName("Paralisado: não age; para reagir passa em Vontade ND 15 (falhou -> sem reação), e reage com desvantagem")
    void paralyzedReaction() {
        cond(pN, "PARALISADO");
        AttackResponse r = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), master.getId());
        queue.add(msg(15, 10, 7));
        queue.add(msg(10, 9, null)); // Vontade 10 < 15: não reage
        AttackResponse.TargetResponse t = service.respond(r.id(), r.targets().get(0).id(), ReactionType.DODGE, master.getId())
                .targets().get(0);
        assertThat(t.reaction()).isEqualTo(ReactionType.NONE);
        assertThat(t.outcome()).isEqualTo(AttackOutcome.HIT);

        pA.setPaCurrent(5);
        sheetA.setCurrentSp(10);
        AttackResponse r2 = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), master.getId());
        queue.add(msg(15, 10, 7));
        queue.add(msg(16, 14, null)); // Vontade ok
        queue.add(msg(18, 15, null)); // esquiva
        AttackResponse.TargetResponse t2 = service.respond(r2.id(), r2.targets().get(0).id(), ReactionType.DODGE, master.getId())
                .targets().get(0);
        assertThat(t2.outcome()).isEqualTo(AttackOutcome.DODGED);
        assertThat(lastAdv.get(lastAdv.size() - 1)).isEqualTo(-1);

        cond(pA, "PARALISADO");
        pA.setPaCurrent(5);
        assertThatThrownBy(() -> service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId()))
                .isInstanceOf(InvalidCombatException.class);
    }

    @Test
    @DisplayName("§31: base do defensor soma Guarda/Penalidade na reação certa (previsto e rolagem)")
    void defenderBaseInReactions() {
        com.tatakai.manager.entity.AssemblyItem g = new com.tatakai.manager.entity.AssemblyItem();
        g.setModule("Guarda");
        g.setLabel("Guarda +3 em Defender");
        com.tatakai.manager.entity.AssemblyItem pen = new com.tatakai.manager.entity.AssemblyItem();
        pen.setModule("Penalidade");
        pen.setLabel("Penalidade −2 em Esquiva");
        pN.setActiveBaseId(UUID.randomUUID());
        lenient().when(profiles.baseProfile(eq(npcSheet), eq(pN.getActiveBaseId())))
                .thenReturn(com.tatakai.manager.service.rules.BaseRules.profile("Fudo Dachi", List.of(g, pen), false));

        AttackResponse r = service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId());
        AttackResponse.TargetResponse t = service.list(enc.getId(), AttackStatus.AWAITING_REACTIONS, master.getId()).stream()
                .filter(x -> x.id().equals(r.id())).findFirst().get().targets().get(0);
        int defend = t.options().stream().filter(o -> o.reaction() == ReactionType.DEFEND).findFirst().get().predictedBonus();
        int dodge = t.options().stream().filter(o -> o.reaction() == ReactionType.DODGE).findFirst().get().predictedBonus();
        assertThat(defend - (com.tatakai.manager.service.rules.RollRules.modOrZero(npcSheet.getCon()))).isEqualTo(3);
        assertThat(dodge - (com.tatakai.manager.service.rules.RollRules.modOrZero(npcSheet.getDex()))).isEqualTo(-2);

        queue.add(msg(15, 10, 5));
        queue.add(msg(18, 15, null));
        service.respond(r.id(), t.id(), ReactionType.DEFEND, master.getId());
        assertThat(rollCalcs.get(rollCalcs.size() - 1).hit())
                .anyMatch(part -> part.source().startsWith("Guarda (base: Fudo Dachi)") && part.value() == 3);
    }

    @Test
    @DisplayName("Crítico Garantido: alvo inconsciente (não por HP) ou golpe localizado em ferimento não tratado")
    void guaranteedCrit() {
        cList.add(CombatCondition.builder().id(UUID.randomUUID()).participantId(pN.getId()).conditionKey("INCONSCIENTE")
                .note("PQ").expiresAt(ConditionExpiry.COUNTER).turnsRemaining(3).build());
        queue.add(msg(5, 4, 3));
        service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId());
        assertThat(rollCalcs.get(rollCalcs.size() - 1).critMargin()).isEqualTo(1);
        assertThat(rollCalcs.get(rollCalcs.size() - 1).hit()).anyMatch(p -> p.source().contains("alvo inconsciente"));

        // inconsciente por HP não garante
        cList.get(cList.size() - 1).setNote("HP");
        pA.setPaCurrent(5);
        sheetA.setCurrentSp(10);
        queue.add(msg(5, 4, 3));
        service.declare(enc.getId(), technique(AttackActionKind.MAJOR, pN), player.getId());
        assertThat(rollCalcs.get(rollCalcs.size() - 1).critMargin()).isEqualTo(20);

        // ferimento não tratado no braço + ataque localizado no braço
        cList.clear();
        npcSheet.getInjuries().add(PermanentInjury.builder().type(InjuryType.BLUNT).region("Braço esquerdo (Membro Inútil)")
                .severity(InjurySeverity.MINOR).build());
        pA.setPaCurrent(5);
        sheetA.setCurrentSp(10);
        service.declare(enc.getId(), new CreateAttackRequest(pA.getId(), RollSourceType.TECHNIQUE, tech.getId(), null, null,
                AttackActionKind.MAJOR, List.of(pN.getId()), "braço", -2), player.getId());
        queue.add(msg(15, 10, 5));
        AttackResponse r = service.list(enc.getId(), AttackStatus.AWAITING_REACTIONS, master.getId()).get(0);
        service.respond(r.id(), r.targets().get(0).id(), ReactionType.NONE, master.getId());
        assertThat(rollCalcs.get(rollCalcs.size() - 1).critMargin()).isEqualTo(1);
    }
}
