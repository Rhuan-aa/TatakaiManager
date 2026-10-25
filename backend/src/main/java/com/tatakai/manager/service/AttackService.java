package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.CreateAttackRequest;
import com.tatakai.manager.dto.response.AttackResponse;
import com.tatakai.manager.dto.response.AttackResponse.Option;
import com.tatakai.manager.dto.response.AttackResponse.TargetResponse;
import com.tatakai.manager.dto.response.CombatUpdateMessage;
import com.tatakai.manager.dto.response.RollMessage;
import com.tatakai.manager.entity.ActivityCategory;
import com.tatakai.manager.entity.ActorRole;
import com.tatakai.manager.entity.AttackActionKind;
import com.tatakai.manager.entity.AttackOutcome;
import com.tatakai.manager.entity.AttackStatus;
import com.tatakai.manager.entity.AttackTarget;
import com.tatakai.manager.entity.BasicAttack;
import com.tatakai.manager.entity.CharacterSheet;
import com.tatakai.manager.entity.CharacterTechnique;
import com.tatakai.manager.entity.CombatCondition;
import com.tatakai.manager.entity.CombatEvent;
import com.tatakai.manager.entity.CombatEventType;
import com.tatakai.manager.entity.Encounter;
import com.tatakai.manager.entity.EncounterParticipant;
import com.tatakai.manager.entity.EncounterStatus;
import com.tatakai.manager.entity.PendingAttack;
import com.tatakai.manager.entity.Dominance;
import com.tatakai.manager.entity.StrikeSide;
import com.tatakai.manager.entity.Guard;
import com.tatakai.manager.entity.LateCounter;
import com.tatakai.manager.entity.MartialType;
import com.tatakai.manager.entity.ReactionType;
import com.tatakai.manager.entity.Hysteria;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.dto.request.LateCounterRequest;
import com.tatakai.manager.service.rules.MartialRules;
import com.tatakai.manager.service.rules.SheetRules;
import com.tatakai.manager.service.rules.VitalRules;
import com.tatakai.manager.entity.RollPart;
import com.tatakai.manager.entity.RollSourceType;
import com.tatakai.manager.entity.TechniqueState;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.exception.CombatConflictException;
import com.tatakai.manager.exception.EncounterNotFoundException;
import com.tatakai.manager.exception.InvalidCombatException;
import com.tatakai.manager.repository.AttackTargetRepository;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CharacterSheetRepository;
import com.tatakai.manager.repository.CharacterTechniqueRepository;
import com.tatakai.manager.repository.CombatConditionRepository;
import com.tatakai.manager.repository.CombatEventRepository;
import com.tatakai.manager.repository.EncounterParticipantRepository;
import com.tatakai.manager.repository.EncounterRepository;
import com.tatakai.manager.repository.PendingAttackRepository;
import com.tatakai.manager.service.RollProfileService.Calc;
import com.tatakai.manager.service.rules.AttackRules;
import com.tatakai.manager.service.rules.CombatRules;
import com.tatakai.manager.service.rules.EffectRules;
import com.tatakai.manager.service.rules.BaseRules;
import com.tatakai.manager.entity.AssemblyItem;
import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.Roll;
import com.tatakai.manager.service.rules.RollRules;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.Random;
import java.util.stream.Collectors;

/** Transação de ataque e reação (F6b): declarar, reagir, cancelar e resolver. */
@Service
@Transactional
public class AttackService {

    private static final String FURTIVE = "FURTIVO";
    private static final String KNOCKED_DOWN = "DERRUBADO";
    private static final String DISORIENTED = "DESORIENTADO";
    private static final String RESTRAINED = "IMOBILIZADO";
    private static final Set<String> NO_REACTION = Set.of("VULNERAVEL", "INCONSCIENTE", "MORTO");
    /** Reagem com desvantagem; Atordoado não contra-ataca, Paralisado precisa de Vontade ND 15 antes. */
    private static final String STUNNED = "ATORDOADO";
    private static final String PARALYZED = "PARALISADO";
    public static final int PARALYZED_REACTION_ND = 15;
    private static final Random DICE = new java.security.SecureRandom();
    private static final Set<String> EASY_TARGET = Set.of("AGARRADO", "IMOBILIZADO", "PARALISADO");

    private final EncounterRepository encounters;
    private final EncounterParticipantRepository participants;
    private final CombatConditionRepository conditions;
    private final CombatEventRepository timeline;
    private final PendingAttackRepository attacks;
    private final AttackTargetRepository targets;
    private final CharacterSheetRepository sheets;
    private final CharacterTechniqueRepository techniques;
    private final CampaignMemberRepository members;
    private final RollService rolls;
    private final RollProfileService profiles;
    private final SheetActivityLogService activityLog;
    private final ApplicationEventPublisher publisher;
    private final VitalService vital;
    private final MartialService martial;

    public AttackService(EncounterRepository encounters, EncounterParticipantRepository participants,
                         CombatConditionRepository conditions, CombatEventRepository timeline,
                         PendingAttackRepository attacks, AttackTargetRepository targets,
                         CharacterSheetRepository sheets, CharacterTechniqueRepository techniques,
                         CampaignMemberRepository members, RollService rolls, RollProfileService profiles,
                         SheetActivityLogService activityLog, ApplicationEventPublisher publisher,
                         VitalService vital, MartialService martial) {
        this.encounters = encounters;
        this.participants = participants;
        this.conditions = conditions;
        this.timeline = timeline;
        this.attacks = attacks;
        this.targets = targets;
        this.sheets = sheets;
        this.techniques = techniques;
        this.members = members;
        this.rolls = rolls;
        this.profiles = profiles;
        this.activityLog = activityLog;
        this.publisher = publisher;
        this.vital = vital;
        this.martial = martial;
    }

    // ---------- contexto ----------

    private Encounter load(UUID id) {
        return encounters.findById(id).orElseThrow(() -> new EncounterNotFoundException("Encontro não encontrado"));
    }

    private Role requireMembership(UUID campaignId, UUID userId) {
        return members.findByCampaignIdAndUserId(campaignId, userId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha")).getRole();
    }

    private static UUID ownerUserId(CharacterSheet s) {
        return s.isNpcSheet() ? null : s.getCampaignMember().getUser().getId();
    }

    /** Participantes, fichas e condições do encontro, carregados uma vez por requisição. */
    private final class Ctx {
        final Encounter enc;
        final Map<UUID, EncounterParticipant> ps;
        final List<EncounterParticipant> ordered;
        final Map<UUID, CharacterSheet> sheetOf;
        final Map<UUID, Set<String>> keys;

        Ctx(Encounter e) {
            enc = e;
            ordered = participants.findByEncounterIdOrderByOrderIndex(e.getId());
            ps = ordered.stream().collect(Collectors.toMap(EncounterParticipant::getId, Function.identity()));
            Map<UUID, CharacterSheet> bySheet = sheets.findAllById(
                    ordered.stream().map(EncounterParticipant::getSheetId).toList())
                    .stream().collect(Collectors.toMap(CharacterSheet::getId, Function.identity()));
            sheetOf = new java.util.HashMap<>();
            ordered.forEach(p -> sheetOf.put(p.getId(), bySheet.get(p.getSheetId())));
            keys = new java.util.HashMap<>();
            if (!ps.isEmpty()) {
                for (CombatCondition c : conditions.findByParticipantIdIn(ps.keySet())) {
                    keys.computeIfAbsent(c.getParticipantId(), k -> new HashSet<>()).add(c.getConditionKey());
                }
            }
        }

        EncounterParticipant participant(UUID id) {
            EncounterParticipant p = ps.get(id);
            if (p == null) throw new EncounterNotFoundException("Participante não encontrado");
            return p;
        }

        boolean has(UUID participantId, String key) {
            return keys.getOrDefault(participantId, Set.of()).contains(key);
        }

        boolean hasAny(UUID participantId, Set<String> any) {
            return keys.getOrDefault(participantId, Set.of()).stream().anyMatch(any::contains);
        }
    }

    // ---------- declarar ----------

    public AttackResponse declare(UUID encounterId, CreateAttackRequest req, UUID userId) {
        Encounter e = load(encounterId);
        boolean master = requireMembership(e.getCampaignId(), userId) == Role.MASTER;
        if (e.getStatus() != EncounterStatus.ACTIVE) throw new CombatConflictException("O encontro não está em andamento");
        Ctx ctx = new Ctx(e);
        EncounterParticipant att = ctx.participant(req.attackerId());
        CharacterSheet s = ctx.sheetOf.get(att.getId());
        if (!master && !userId.equals(ownerUserId(s))) {
            throw new AccessDeniedException("Você só pode agir pelo seu próprio personagem");
        }
        if (!ctx.ordered.get(e.getCurrentIndex()).getId().equals(att.getId())) {
            throw new InvalidCombatException("Não é o turno de " + att.getName());
        }
        List<UUID> targetIds = req.targetIds().stream().distinct().toList();
        for (UUID tid : targetIds) {
            EncounterParticipant t = ctx.participant(tid);
            if (t.getId().equals(att.getId())) throw new InvalidCombatException("Não é possível atacar a si mesmo");
            if (ctx.has(t.getId(), FURTIVE)) throw new InvalidCombatException(t.getName() + " está Furtivo e não pode ser alvo");
        }
        if (req.sourceType() != RollSourceType.TECHNIQUE && req.sourceType() != RollSourceType.BASIC_ATTACK) {
            throw new InvalidCombatException("Fonte de ataque inválida");
        }
        PendingAttack a = PendingAttack.builder().encounterId(e.getId()).attackerId(att.getId())
                .sourceType(req.sourceType()).sourceId(req.sourceId()).variation(req.variation())
                .basicAttack(req.basicAttack()).actionKind(req.actionKind()).region(req.region())
                .regionPenalty(req.regionPenalty() == null ? 0 : req.regionPenalty()).strikeSide(req.strikeSide())
                .status(AttackStatus.AWAITING_REACTIONS).round(e.getRound()).sourceName("").build();
        if (ctx.hasAny(att.getId(), Set.of("INCONSCIENTE", "MORTO"))) {
            throw new InvalidCombatException(att.getName() + " está fora de combate e não pode agir");
        }
        if (ctx.has(att.getId(), PARALYZED)) throw new InvalidCombatException(att.getName() + " está Paralisado e não pode agir");
        Calc calc = calcFor(s, a, ctx.has(att.getId(), KNOCKED_DOWN), att, s);
        a.setVariation(calc.variation());
        String name = calc.variation() == null ? calc.name() : calc.name() + " (" + calc.variation() + ")";
        a.setSourceName(name.length() > 100 ? name.substring(0, 100) : name);

        boolean slow = false;
        boolean quick = a.getBasicAttack() == BasicAttack.SOCO;
        if (a.getSourceType() == RollSourceType.TECHNIQUE) {
            CharacterTechnique t = technique(s, a);
            slow = profiles.hasModule(s, t, a.getVariation(), "Lenta");
            quick = profiles.hasModule(s, t, a.getVariation(), "Técnica Rápida");
        }
        if (a.getActionKind() == AttackActionKind.MINOR_OFFENSIVE && ctx.has(att.getId(), "DISSOCIADO")) {
            throw new InvalidCombatException("Dissociado: Ações Menores estão bloqueadas");
        }
        if (a.getActionKind() == AttackActionKind.MINOR_OFFENSIVE && !quick) {
            throw new InvalidCombatException("Ação Menor Ofensiva exige Técnica Rápida ou o ataque básico Soco");
        }
        int pa = AttackRules.paCost(a.getActionKind(), slow);
        int sp = a.getSourceType() == RollSourceType.BASIC_ATTACK
                ? VitalRules.adjustSp(1, VitalRules.tier(att.getAdrenaline()), ctx.has(att.getId(), "DISSOCIADO"))
                : calc.spCost();
        if (sp > 0 && ctx.has(att.getId(), "EXAUSTO")) {
            throw new InvalidCombatException("Exausto: sem técnicas nem ações com custo de SP");
        }
        if (att.getPaCurrent() < pa) {
            throw new InvalidCombatException("PA insuficiente: custa " + pa + " e restam " + att.getPaCurrent());
        }
        if (s.getCurrentSp() < sp) {
            throw new InvalidCombatException("SP insuficiente: custa " + sp + " e restam " + s.getCurrentSp());
        }
        ActorRole role = master ? ActorRole.MASTER : ActorRole.PLAYER;
        att.setPaCurrent(att.getPaCurrent() - pa);
        att.setEngagedThisRound(true);
        vital.applySp(e, att, s, -sp, VitalService.Ctx.of(userId, role, "Ataque: " + a.getSourceName()));
        a.setReservedPa(pa);
        a.setReservedSp(sp);
        a = attacks.save(a);

        List<AttackTarget> ts = new ArrayList<>();
        for (UUID tid : targetIds) {
            ctx.participant(tid).setEngagedThisRound(true);
            boolean auto = ctx.hasAny(tid, NO_REACTION)
                    && !(ctx.has(tid, VitalService.UNCONSCIOUS) && !ctx.has(tid, "MORTO")
                         && !guardians(ctx.participant(tid), att, ctx).isEmpty());
            ts.add(targets.save(AttackTarget.builder().attackId(a.getId()).targetId(tid)
                    .reaction(auto ? ReactionType.NONE : null).autoResolved(auto)
                    .respondedAt(auto ? Instant.now() : null).build()));
        }
        log(e, att.getId(), att.getName() + " declara " + a.getSourceName() + " contra "
                + names(ctx, ts) + " (" + pa + " PA" + (sp > 0 ? ", " + sp + " SP" : "") + ")");
        if (ts.stream().allMatch(t -> t.getReaction() != null)) resolve(e, a, ts, ctx, userId, role);
        return done(e, a, ts, ctx, userId, master);
    }

    // ---------- reagir ----------

    public AttackResponse respond(UUID attackId, UUID targetRowId, ReactionType reaction, UUID userId) {
        return respond(attackId, targetRowId, reaction, null, userId);
    }

    public AttackResponse respond(UUID attackId, UUID targetRowId, ReactionType reaction, UUID defenderId, UUID userId) {
        PendingAttack a = attacks.findById(attackId).orElseThrow(() -> new EncounterNotFoundException("Ataque não encontrado"));
        Encounter e = load(a.getEncounterId());
        boolean master = requireMembership(e.getCampaignId(), userId) == Role.MASTER;
        if (a.getStatus() != AttackStatus.AWAITING_REACTIONS) throw new CombatConflictException("O ataque já foi encerrado");
        Ctx ctx = new Ctx(e);
        List<AttackTarget> ts = targets.findByAttackIdIn(List.of(a.getId()));
        AttackTarget t = ts.stream().filter(x -> x.getId().equals(targetRowId)).findFirst()
                .orElseThrow(() -> new EncounterNotFoundException("Alvo não encontrado"));
        CharacterSheet ts0 = ctx.sheetOf.get(t.getTargetId());
        if (t.getReaction() != null) throw new CombatConflictException("Este alvo já respondeu");
        if (ctx.has(t.getTargetId(), VitalService.UNCONSCIOUS)) {
            // alvo Inconsciente: só um aliado reage no lugar dele, Defendendo ou Contra-atacando
            List<EncounterParticipant> gs = guardians(ctx.participant(t.getTargetId()), ctx.participant(a.getAttackerId()), ctx);
            if (reaction == ReactionType.NONE) {
                if (!master && gs.stream().noneMatch(g -> userId.equals(ownerUserId(ctx.sheetOf.get(g.getId()))))) {
                    throw new AccessDeniedException("Apenas o Mestre ou o dono de um aliado pode responder");
                }
            } else {
                if (reaction == ReactionType.DODGE) throw new InvalidCombatException("Por um aliado inconsciente só é possível Defender ou Contra-atacar");
                EncounterParticipant g = gs.stream().filter(x -> x.getId().equals(defenderId)).findFirst()
                        .orElseThrow(() -> new InvalidCombatException("Escolha um aliado consciente para reagir"));
                CharacterSheet gsh = ctx.sheetOf.get(g.getId());
                if (!master && !userId.equals(ownerUserId(gsh))) {
                    throw new AccessDeniedException("Apenas o Mestre ou o dono do aliado pode reagir por ele");
                }
                if (reaction == ReactionType.COUNTER && (ctx.has(g.getId(), STUNNED) || gsh.getCurrentSp() < 1)) {
                    throw new InvalidCombatException(ctx.has(g.getId(), STUNNED) ? "Atordoado não pode Contra-atacar"
                            : "Contra-ataque exige 1 SP");
                }
                t.setDefenderId(g.getId());
            }
            t.setReaction(reaction);
            t.setRespondedAt(Instant.now());
            targets.save(t);
            if (ts.stream().allMatch(x -> x.getReaction() != null)) {
                resolve(e, a, ts, ctx, userId, master ? ActorRole.MASTER : ActorRole.PLAYER);
            }
            return done(e, a, ts, ctx, userId, master);
        }
        if (!master && !userId.equals(ownerUserId(ts0))) {
            throw new AccessDeniedException("Apenas o dono do alvo ou o Mestre pode reagir");
        }
        if (reaction == ReactionType.COUNTER && ctx.has(t.getTargetId(), STUNNED)) {
            throw new InvalidCombatException("Atordoado não pode Contra-atacar");
        }
        if (reaction == ReactionType.COUNTER && ts0.getCurrentSp() < 1) {
            throw new InvalidCombatException("Contra-ataque exige 1 SP");
        }
        t.setReaction(reaction);
        t.setRespondedAt(Instant.now());
        targets.save(t);
        if (ts.stream().allMatch(x -> x.getReaction() != null)) {
            resolve(e, a, ts, ctx, userId, master ? ActorRole.MASTER : ActorRole.PLAYER);
        }
        return done(e, a, ts, ctx, userId, master);
    }

    // ---------- cancelar ----------

    public AttackResponse cancel(UUID attackId, UUID userId) {
        PendingAttack a = attacks.findById(attackId).orElseThrow(() -> new EncounterNotFoundException("Ataque não encontrado"));
        Encounter e = load(a.getEncounterId());
        boolean master = requireMembership(e.getCampaignId(), userId) == Role.MASTER;
        Ctx ctx = new Ctx(e);
        EncounterParticipant att = ctx.participant(a.getAttackerId());
        CharacterSheet s = ctx.sheetOf.get(att.getId());
        if (!master && !userId.equals(ownerUserId(s))) {
            throw new AccessDeniedException("Apenas o atacante ou o Mestre pode cancelar");
        }
        List<AttackTarget> ts = targets.findByAttackIdIn(List.of(a.getId()));
        if (a.getStatus() != AttackStatus.AWAITING_REACTIONS
                || ts.stream().anyMatch(t -> t.getReaction() != null && !t.isAutoResolved())) {
            throw new CombatConflictException("O ataque não pode mais ser cancelado");
        }
        att.setPaCurrent(att.getPaCurrent() + a.getReservedPa());
        vital.applySp(e, att, s, a.getReservedSp(), VitalService.Ctx.of(userId,
                master ? ActorRole.MASTER : ActorRole.PLAYER, "Ataque cancelado: " + a.getSourceName()));
        a.setStatus(AttackStatus.CANCELLED);
        attacks.save(a);
        log(e, att.getId(), att.getName() + " cancelou " + a.getSourceName());
        return done(e, a, ts, ctx, userId, master);
    }

    // ---------- consulta ----------

    @Transactional(readOnly = true)
    public List<AttackResponse> list(UUID encounterId, AttackStatus status, UUID userId) {
        Encounter e = load(encounterId);
        boolean master = requireMembership(e.getCampaignId(), userId) == Role.MASTER;
        Ctx ctx = new Ctx(e);
        List<PendingAttack> as = status == null ? attacks.findByEncounterIdOrderByCreatedAtDesc(e.getId())
                : attacks.findByEncounterIdAndStatusOrderByCreatedAtDesc(e.getId(), status);
        if (as.isEmpty()) return List.of();
        Map<UUID, List<AttackTarget>> byAttack = targets.findByAttackIdIn(as.stream().map(PendingAttack::getId).toList())
                .stream().collect(Collectors.groupingBy(AttackTarget::getAttackId));
        return as.stream().map(a -> view(a, byAttack.getOrDefault(a.getId(), List.of()), ctx, userId, master)).toList();
    }

    private AttackResponse done(Encounter e, PendingAttack a, List<AttackTarget> ts, Ctx ctx, UUID userId, boolean master) {
        publisher.publishEvent(new CombatUpdateMessage(e.getCampaignId(), e.getId(), "ATTACK"));
        return view(a, ts, ctx, userId, master);
    }

    private AttackResponse view(PendingAttack a, List<AttackTarget> ts, Ctx ctx, UUID userId, boolean master) {
        EncounterParticipant att = ctx.ps.get(a.getAttackerId());
        List<TargetResponse> out = new ArrayList<>();
        for (AttackTarget t : ts) {
            EncounterParticipant tp = ctx.ps.get(t.getTargetId());
            CharacterSheet tsh = ctx.sheetOf.get(t.getTargetId());
            boolean open = a.getStatus() == AttackStatus.AWAITING_REACTIONS && t.getReaction() == null && tsh != null;
            List<AttackResponse.Guardian> guards = null;
            boolean can;
            if (open && tp != null && att != null && ctx.has(tp.getId(), VitalService.UNCONSCIOUS)) {
                guards = guardians(tp, att, ctx).stream()
                        .filter(g -> master || userId.equals(ownerUserId(ctx.sheetOf.get(g.getId()))))
                        .map(g -> new AttackResponse.Guardian(g.getId(), g.getName(),
                                options(a, att, g, ctx.sheetOf.get(g.getId()), ctx).stream()
                                        .filter(o -> o.reaction() != ReactionType.DODGE).toList()))
                        .toList();
                can = !guards.isEmpty();
            } else {
                can = open && (master || userId.equals(ownerUserId(tsh)));
            }
            EncounterParticipant dp = t.getDefenderId() == null ? null : ctx.ps.get(t.getDefenderId());
            out.add(new TargetResponse(t.getId(), t.getTargetId(), tp == null ? null : tp.getName(), t.getReaction(),
                    t.isAutoResolved(), can, can && guards == null ? options(a, att, tp, tsh, ctx) : null,
                    t.getReactionRollId() == null ? null : rolls.message(t.getReactionRollId()), t.getOutcome(),
                    t.getMitigationPct(), t.getDamage(), t.getCounterDamage(), t.getLateCounter(),
                    t.getLateCounterDamage(), canLateCounter(a, t, ctx, tsh, userId, master),
                    t.getDefenderId(), dp == null ? null : dp.getName(), guards));
        }
        return new AttackResponse(a.getId(), a.getEncounterId(), a.getAttackerId(), att == null ? null : att.getName(),
                a.getSourceName(), a.getActionKind(), a.getRegion(), a.getReservedPa(), a.getReservedSp(), a.getStatus(),
                a.getRound(), a.getAttackRollId() == null ? null : rolls.message(a.getAttackRollId()), out);
    }

    /** Janela do Contra-Ataque Tardio: alvo Defendeu/Esquivou e o turno ainda não acabou (RN-REA-04). */
    private static boolean canLateCounter(PendingAttack a, AttackTarget t, Ctx ctx, CharacterSheet tsh, UUID userId,
                                          boolean master) {
        return a.getStatus() == AttackStatus.RESOLVED && t.getLateCounter() == null
                && (t.getOutcome() == AttackOutcome.DEFENDED || t.getOutcome() == AttackOutcome.DODGED)
                && ctx.enc.getStatus() == EncounterStatus.ACTIVE && a.getResolvedTurn() != null
                && a.getResolvedTurn() == ctx.enc.getTurnCounter()
                && tsh != null && (master || userId.equals(ownerUserId(tsh)));
    }

    private List<Option> options(PendingAttack a, EncounterParticipant att, EncounterParticipant tp, CharacterSheet tsh,
                                 Ctx ctx) {
        int common = hysteriaReaction(tp) - CombatRules.fatiguePenalty(tsh.getPhysicalFatigue())
                + AttackRules.groupPenalty(countAttackers(tp, att.getId()))
                + AttackRules.timingBonus(uses(a, att, tp))
                + martial.read(tsh, att, ctx.sheetOf.get(att.getId()))
                + (incomum(att, ctx.sheetOf.get(att.getId()), tp, tsh, ctx) ? 1 : 0);
        boolean sp = tsh.getCurrentSp() >= 1;
        boolean stunned = ctx.has(tp.getId(), STUNNED);
        BaseRules.Profile db = profiles.baseProfile(tsh, tp.getActiveBaseId());
        java.util.function.ToIntFunction<ReactionType> base = r -> db == null ? 0 : db.reaction(r);
        return List.of(
                new Option(ReactionType.DEFEND, true, null, RollRules.modOrZero(tsh.getCon()) + common + base.applyAsInt(ReactionType.DEFEND)),
                new Option(ReactionType.DODGE, true, null, RollRules.modOrZero(tsh.getDex()) + common + base.applyAsInt(ReactionType.DODGE)),
                new Option(ReactionType.COUNTER, sp && !stunned, stunned ? "Atordoado: sem Contra-ataque" : sp ? null : "SP insuficiente",
                        RollRules.modOrZero(tsh.getDex()) + common + base.applyAsInt(ReactionType.COUNTER)),
                new Option(ReactionType.NONE, true, null, 0));
    }

    // ---------- resolver ----------

    /** Interação marcial de um alvo: +1 atacante, −1 defensor, 0 nada; label vai para o breakdown. */
    private record Inter(int result, String label) {
        static final Inter NONE = new Inter(0, null);
    }

    /** Defensor de um alvo: seu lutador marcial e a interação com o atacante. */
    private record Matchup(MartialService.Fighter defender, Inter inter) {}

    private Inter interaction(MartialService.Fighter fa, CharacterTechnique tech, EncounterParticipant att,
                              CharacterSheet attSheet, MartialService.Fighter fd, EncounterParticipant tp,
                              CharacterSheet tsh) {
        MartialType at = martial.attackType(fa, tech);
        MartialType dt = fd.type();
        int w = MartialRules.winner(at, dt);
        if (w == 0) return Inter.NONE;
        CharacterTechnique atkBase = martial.baseOrNull(attSheet, att.getActiveBaseId());
        CharacterTechnique defBase = martial.baseOrNull(tsh, tp.getActiveBaseId());
        boolean atkJoker = martial.hasJoker(tech) || martial.hasJoker(atkBase);
        int r = MartialRules.interaction(at, dt, atkJoker, martial.hasJoker(defBase));
        if (r == 0) return new Inter(0, "Interação anulada por Coringa");
        return new Inter(r, "Interação: " + (r == 1 ? at.label() + " > " + dt.label() : dt.label() + " > " + at.label()));
    }

    private void resolve(Encounter e, PendingAttack a, List<AttackTarget> ts, Ctx ctx, UUID userId, ActorRole role) {
        EncounterParticipant att = ctx.participant(a.getAttackerId());
        CharacterSheet attSheet = ctx.sheetOf.get(att.getId());
        boolean downed = ctx.has(att.getId(), KNOCKED_DOWN);
        Calc calc = calcFor(attSheet, a, downed, att, attSheet);
        VitalService.Ctx vctx = VitalService.Ctx.of(userId, role, "Ataque de " + att.getName() + ": " + a.getSourceName());
        CharacterTechnique tech = a.getSourceType() == RollSourceType.TECHNIQUE ? technique(attSheet, a) : null;
        boolean breaksGuard = tech != null && profiles.hasModule(attSheet, tech, a.getVariation(), "Romper Guarda");

        MartialService.Fighter fa = martial.fighter(att, attSheet);
        Map<UUID, Matchup> matchups = new java.util.HashMap<>();
        for (AttackTarget t : ts) {
            EncounterParticipant tp = ctx.participant(t.getTargetId());
            CharacterSheet tsh = ctx.sheetOf.get(tp.getId());
            MartialService.Fighter fd = martial.fighter(tp, tsh);
            matchups.put(tp.getId(), new Matchup(fd, interaction(fa, tech, att, attSheet, fd, tp, tsh)));
        }
        calc = withMartial(calc, a, att, attSheet, downed, fa, tech, matchups.get(ts.get(0).getTargetId()).inter());

        int adv = (ctx.has(att.getId(), RESTRAINED) ? -1 : 0) + (ctx.has(att.getId(), DISORIENTED) ? -1 : 0)
                + (ctx.hasAny(ts.get(0).getTargetId(), EASY_TARGET) ? 1 : 0)
                + (ctx.has(att.getId(), "DISSOCIADO") ? -1 : 0)
                + (ctx.has(att.getId(), VitalService.BURNING) ? -1 : 0); // Queimando: desvantagem em precisão
        String targetNames = names(ctx, ts);
        RollMessage atk = rolls.rollCalc(attSheet, e.getCampaignId(), calc, adv,
                "Ataque: " + att.getName() + " → " + targetNames, userId, role);
        a.setAttackRollId(atk.id());
        String moveArt = tech == null || tech.getCharacterArt() == null ? null : tech.getCharacterArt().getArtName();
        if (tech != null) martial.fire(e, att, attSheet, "ART_TECHNIQUE_USED", null, moveArt);
        int attackerCalejo = profiles.calejo(attSheet, att.getActiveBaseId());

        for (AttackTarget t : ts) {
            EncounterParticipant tp = ctx.participant(t.getTargetId());
            CharacterSheet tsh = ctx.sheetOf.get(tp.getId());
            List<UUID> group = new ArrayList<>(tp.getAttackersThisRound() == null ? List.of() : tp.getAttackersThisRound());
            if (!group.contains(att.getId())) group.add(att.getId());
            tp.setAttackersThisRound(group);

            // quem reage: o próprio alvo ou o aliado que o defende (alvo Inconsciente)
            EncounterParticipant rp = t.getDefenderId() == null ? tp : ctx.participant(t.getDefenderId());
            CharacterSheet rsh = ctx.sheetOf.get(rp.getId());
            Matchup mu = rp == tp ? matchups.get(tp.getId()) : guardianMatchup(fa, tech, att, attSheet, rp, rsh);
            if (rp != tp) log(e, rp.getId(), rp.getName() + " reage no lugar de " + tp.getName());

            RollMessage rmsg = null;
            if (t.getReaction() != ReactionType.NONE && !atk.fumble() && ctx.has(rp.getId(), PARALYZED)
                    && !vital.willTest(e, rp, rsh, PARALYZED_REACTION_ND, "Paralisado: reagir",
                            VitalService.Ctx.of(userId, role, "Paralisado"))) {
                log(e, rp.getId(), rp.getName() + " não consegue reagir (Paralisado)");
                t.setReaction(ReactionType.NONE);
            }
            if (t.getReaction() != ReactionType.NONE && !atk.fumble()) {
                rmsg = reactionRoll(e, a, att, attSheet, rp, rsh, t.getReaction(), group.size(), ctx, mu, userId, role);
                t.setReactionRollId(rmsg.id());
                if (t.getReaction() == ReactionType.COUNTER) {
                    vital.applySp(e, rp, rsh, -1, VitalService.Ctx.of(userId, role, "Contra-ataque"));
                }
            }
            AttackRules.Result r = AttackRules.resolve(t.getReaction(), atk.total(), atk.critical(), atk.fumble(),
                    rmsg == null ? 0 : rmsg.total(), rmsg != null && rmsg.critical(), breaksGuard);
            if (t.getReaction() == ReactionType.NONE && !atk.critical() && ctx.has(tp.getId(), "VULNERAVEL")
                    && AttackRules.missesVulnerable(atk.total())) {
                r = new AttackRules.Result(AttackOutcome.MISS, 0, false);
                log(e, tp.getId(), att.getName() + " errou " + tp.getName() + " (Vulnerável: " + atk.total()
                        + " vs ND " + AttackRules.VULNERABLE_ND + ")");
            }
            int calejo = profiles.calejo(tsh, tp.getActiveBaseId());
            boolean connects = r.outcome() != AttackOutcome.MISS && r.outcome() != AttackOutcome.DODGED;
            int hitDamage = atk.damageTotal() == null ? 0 : atk.damageTotal();
            String sure = connects && !atk.critical() && calc.dice() != null ? guaranteedCrit(a, t, tp, tsh) : null;
            if (sure != null) {
                // Crítico Garantido é só dano crítico: rola de novo os dados de dano do golpe e soma neste alvo
                Roll extra = rolls.rollInternal(attSheet, calc.dice().count() + "d" + calc.dice().sides(),
                        "Crítico Garantido (" + sure + "): " + att.getName() + " → " + tp.getName(), userId, role);
                hitDamage += extra.getTotal();
                log(e, tp.getId(), "Crítico Garantido em " + tp.getName() + " (" + sure + "): +" + extra.getTotal() + " de dano");
            }
            int dmg = connects ? AttackRules.damage(hitDamage, r.mitigationPct(), calejo) : 0;
            int counter = 0;
            if (r.counterHits()) {
                RollMessage c = rolls.rollCalc(rsh, e.getCampaignId(), profiles.basic(rsh, BasicAttack.SOCO,
                        RollProfileService.Opts.NONE), 0, "Contra-ataque: " + rp.getName() + " → " + att.getName(),
                        userId, role);
                int raw = c.damageTotal() == null ? 0 : c.damageTotal();
                counter = raw <= 0 ? 0 : AttackRules.damage(raw, 0, attackerCalejo);
                attacksAttacker(e, att, attSheet, counter, new VitalService.Ctx(userId, role,
                        "Contra-ataque de " + rp.getName(), c.critical()));
            }
            if (dmg > 0) {
                vital.applyHp(e, tp, tsh, -dmg, vctx.withCritical(atk.critical()));
                int kp = VitalRules.kpLoss(a.getRegion(), headBonus(attSheet, a), 0, atk.critical(),
                        VitalRules.activeHysteria(tp.getAdrenaline(), tp.getHysteria()) == Hysteria.WALL);
                if (kp > 0) vital.applyKp(e, tp, tsh, -kp, vctx);
                if (permanentInjury(atk, calc)) vital.permanentInjury(e, tp, tsh, dmg, a.getRegion(), vctx);
                martial.fire(e, att, attSheet, "HIT", tp.getId(), moveArt);
                martial.fire(e, tp, tsh, "GOT_HIT", att.getId(), null);
            }
            if (tech != null && r.outcome() == AttackOutcome.HIT) {
                applyEffects(e, a, tech, att, attSheet, tp, tsh, atk, vctx, userId, role);
            }
            switch (r.outcome()) {
                case DEFENDED -> martial.fire(e, rp, rsh, "DEFEND_SUCCESS", att.getId(), null);
                case DODGED -> martial.fire(e, rp, rsh, "DODGE_SUCCESS", att.getId(), null);
                case COUNTERED -> martial.fire(e, rp, rsh, "COUNTER_SUCCESS", att.getId(), null);
                default -> { }
            }
            t.setOutcome(r.outcome());
            t.setMitigationPct(r.mitigationPct());
            t.setDamage(dmg);
            t.setCounterDamage(counter);
            targets.save(t);
            log(e, att.getId(), summary(att, tp, a, t, counter));
        }
        a.setStatus(AttackStatus.RESOLVED);
        a.setResolvedAt(Instant.now());
        a.setResolvedTurn(e.getTurnCounter());
        attacks.save(a);
    }

    /**
     * Efeitos de módulo no acerto (§30): "no dado" só com 17+ natural; crítico entra sem teste; senão o alvo resiste
     * com o atributo do efeito contra o total do ataque. Efeito sem regra automática vira aviso para o Mestre.
     */
    private void applyEffects(Encounter e, PendingAttack a, CharacterTechnique tech, EncounterParticipant att,
                              CharacterSheet attSheet, EncounterParticipant tp, CharacterSheet tsh, RollMessage atk,
                              VitalService.Ctx vctx, UUID userId, ActorRole role) {
        for (AssemblyItem it : profiles.effects(attSheet, tech, a.getVariation())) {
            if (!EffectRules.triggers(it.isOnDie(), atk.naturalD20(), atk.critical())) continue;
            String label = it.getModule();
            EffectRules.Effect fx = EffectRules.of(label).orElse(null);
            if (fx == null) {
                log(e, tp.getId(), "Efeito " + label + " de " + att.getName() + " em " + tp.getName() + ": aplicar manualmente");
                continue;
            }
            if (fx.resist() != null && !atk.critical()) {
                boolean autoFail = vital.hasCondition(tp, "PARALISADO")
                        && (fx.resist() == Attribute.STR || fx.resist() == Attribute.DEX);
                if (!autoFail) {
                    Calc rc = profiles.attribute(tsh, fx.resist());
                    BaseRules.Profile db = EffectRules.ROOTED.contains(label) ? profiles.baseProfile(tsh, tp.getActiveBaseId()) : null;
                    if (db != null && db.rooted() != 0) {
                        List<RollPart> hit = new ArrayList<>(rc.hit());
                        hit.add(new RollPart("HIT", "Enraizado (base: " + db.name() + ")", db.rooted(), null, false));
                        rc = new Calc(rc.type(), rc.sourceId(), rc.name(), rc.variation(), rc.basic(), rc.spCost(),
                                rc.critMargin(), rc.advantage(), hit, rc.dice(), rc.damage());
                    }
                    RollMessage res = rolls.rollCalc(tsh, e.getCampaignId(), rc, vital.conditionAdvantage(tp, tsh, fx.resist()),
                            "Resistir a " + label + ": " + tp.getName() + " vs " + atk.total(), userId, role);
                    if (EffectRules.resisted(res.total(), atk.total())) {
                        log(e, tp.getId(), tp.getName() + " resistiu a " + label + " (" + res.total() + " vs " + atk.total() + ")");
                        continue;
                    }
                }
            }
            int qty = Math.max(1, it.getQty());
            if (fx.condition() != null) {
                String key = EffectRules.escalate(fx.condition(), vital.hasCondition(tp, "MEIO_CEGO"));
                if (fx.expiry() != null) vital.addCondition(e, tp, key, fx.expiry(), fx.turns(), label);
                else vital.addCondition(e, tp, key, label);
                if ((key.equals(KNOCKED_DOWN) || key.equals("QUEBRA_DE_BASE")) && tp.getActiveBaseId() != null) {
                    tp.setActiveBaseId(null);
                    log(e, tp.getId(), tp.getName() + " perdeu a base ativa");
                }
                if (key.equals(KNOCKED_DOWN) || key.equals("AGARRADO")) {
                    martial.fire(e, tp, tsh, key.equals(KNOCKED_DOWN) ? "KNOCKED_DOWN" : "GRAPPLED", null, null);
                }
            }
            if (fx.takedown()) {
                int dmg = EffectRules.takedownDamage(tsh.getWeightKg());
                log(e, tp.getId(), tp.getName() + " sofre " + dmg + " de dano de Derrubada");
                vital.applyHp(e, tp, tsh, -dmg, vctx.withCritical(false));
            }
            if (fx.stealsPa()) {
                tp.setPaLossNext(tp.getPaLossNext() + qty);
                log(e, tp.getId(), tp.getName() + " perde " + qty + " PA no próximo turno (" + label + ")");
            }
            if (fx.wounds()) vital.openWounds(e, tp, tsh, qty, vctx);
            if (fx.condition() == null && !fx.stealsPa() && !fx.wounds()) {
                log(e, tp.getId(), tp.getName() + " sofre " + label + " (posição na cena: Mestre)");
            }
        }
    }

    /** Parcelas marciais do atacante: especialização (básico), guarda e interação (RN-INT-03, RN-GUA, RN-ATQ-01). */
    private Calc withMartial(Calc c, PendingAttack a, EncounterParticipant att, CharacterSheet s, boolean downed,
                             MartialService.Fighter fa, CharacterTechnique tech, Inter inter) {
        List<RollPart> hit = new ArrayList<>(c.hit());
        List<RollPart> damage = new ArrayList<>(c.damage());
        int adv = c.advantage();
        boolean basic = a.getSourceType() == RollSourceType.BASIC_ATTACK && a.getBasicAttack() != null;
        if (basic && !downed && (!fa.hasArt() || !fa.attack().contains(MartialRules.family(a.getBasicAttack())))) {
            halve(hit, "DES", "Fora da especialização: DES/2");
        }
        StrikeSide side = a.getStrikeSide();
        if (side != null && (tech != null || (basic && MartialRules.oneSided(a.getBasicAttack())))) {
            Guard g = MartialRules.effectiveGuard(att.getGuard(), downed);
            int gh = MartialRules.guardHit(g, side, s.getDominance() == Dominance.AMBIDEXTROUS);
            if (gh != 0) hit.add(new RollPart("HIT", gh > 0 ? "Guarda: frente +1" : "Guarda invertida: trás −1", gh, null, false));
            if (MartialRules.guardBackDamage(g, side)) {
                int face = DICE.nextInt(2) + 1;
                damage.add(new RollPart("DAMAGE", "Guarda: trás +1d2", face, List.of(face), false));
            }
        }
        if (inter.label() != null) hit.add(new RollPart("HIT", inter.label(), 0, null, false));
        if (inter.result() == 1) adv++;
        return new Calc(c.type(), c.sourceId(), c.name(), c.variation(), c.basic(), c.spCost(), c.critMargin(), adv,
                hit, c.dice(), damage);
    }

    /** Troca a parcela `source` pela metade do valor (RN-ATR-04: só se > 0). */
    private static void halve(List<RollPart> hit, String source, String label) {
        for (int i = 0; i < hit.size(); i++) {
            if (source.equals(hit.get(i).source())) {
                hit.set(i, new RollPart("HIT", label, SheetRules.half(hit.get(i).value()), null, false));
                return;
            }
        }
    }

    private static boolean knocked(Ctx ctx, EncounterParticipant p) {
        return ctx.has(p.getId(), KNOCKED_DOWN);
    }

    /** Vantagem do Incomum (+1 na reação do defensor), RN-GUA-01. */
    private boolean incomum(EncounterParticipant att, CharacterSheet attSheet, EncounterParticipant tp,
                            CharacterSheet tsh, Ctx ctx) {
        Guard ga = MartialRules.effectiveGuard(att.getGuard(), knocked(ctx, att));
        Guard gd = MartialRules.effectiveGuard(tp.getGuard(), knocked(ctx, tp));
        return MartialRules.incomum(MartialRules.leadFoot(tsh.getDominance(), tp.getGuard()),
                MartialRules.leadFoot(attSheet.getDominance(), att.getGuard()), gd, ga,
                attSheet.getDominance() == Dominance.AMBIDEXTROUS, martial.read(attSheet, tp, tsh) > 0);
    }

    private RollMessage reactionRoll(Encounter e, PendingAttack a, EncounterParticipant att, CharacterSheet attSheet,
                                     EncounterParticipant tp, CharacterSheet tsh, ReactionType reaction, int attackers,
                                     Ctx ctx, Matchup mu, UUID userId, ActorRole role) {
        boolean defend = reaction == ReactionType.DEFEND;
        List<RollPart> hit = new ArrayList<>();
        int mod = RollRules.modOrZero(defend ? tsh.getCon() : tsh.getDex());
        String attr = defend ? "CON" : "DES";
        if (!mu.defender().hasArt() || !mu.defender().reaction().contains(reaction)) {
            hit.add(new RollPart("HIT", "Fora da especialização: " + attr + "/2", SheetRules.half(mod), null, false));
        } else {
            hit.add(new RollPart("HIT", attr, mod, null, false));
        }
        int fatigue = CombatRules.fatiguePenalty(tsh.getPhysicalFatigue());
        if (fatigue > 0) hit.add(new RollPart("HIT", "Desgaste Físico", -fatigue, null, false));
        int group = AttackRules.groupPenalty(attackers);
        if (group != 0) hit.add(new RollPart("HIT", "Luta contra Grupo", group, null, false));
        int timing = AttackRules.timingBonus(uses(a, att, tp));
        if (timing > 0) hit.add(new RollPart("HIT", "Timing", timing, null, false));
        Hysteria h = VitalRules.activeHysteria(tp.getAdrenaline(), tp.getHysteria());
        if (h == Hysteria.WALL) hit.add(new RollPart("HIT", "Muralha", 4, null, false));
        if (h == Hysteria.FURY) hit.add(new RollPart("HIT", "Fúria", -2, null, false));
        if (h == Hysteria.LIGHTNING) hit.add(new RollPart("HIT", "Relâmpago", 2, null, false));
        int read = martial.read(tsh, att, attSheet);
        if (read > 0) hit.add(new RollPart("HIT", "Leitura de Oponente +" + read, read, null, false));
        if (incomum(att, attSheet, tp, tsh, ctx)) hit.add(new RollPart("HIT", "Vantagem do Incomum +1", 1, null, false));
        int adv = ctx.has(tp.getId(), "DISSOCIADO") ? -1 : 0;
        BaseRules.Profile db = profiles.baseProfile(tsh, tp.getActiveBaseId());
        if (db != null) {
            int g = db.reaction(reaction);
            if (g != 0) hit.add(new RollPart("HIT", (g > 0 ? "Guarda" : "Penalidade") + " (base: " + db.name() + ")", g, null, false));
            MartialType at = attackTypeOf(a, att, attSheet);
            if (db.weakAgainst(at)) adv--;
            if (db.readsAgainst(at)) adv++;
        }
        if (!defend && ctx.has(tp.getId(), KNOCKED_DOWN)) adv--;
        if (reaction == ReactionType.DODGE && ctx.has(tp.getId(), DISORIENTED)) adv--;
        if (ctx.has(tp.getId(), STUNNED) || ctx.has(tp.getId(), PARALYZED)) adv--;
        if (defend && ctx.has(tp.getId(), VitalService.POISONED)) adv--;   // Envenenado: CON
        if (!defend && ctx.has(tp.getId(), VitalService.FREEZING)) adv--;  // Congelando: DES
        if (reaction == ReactionType.DODGE && SheetRules.overloaded(tsh)) adv--; // Sobrecarregado: Esquiva
        if (mu.inter().label() != null) hit.add(new RollPart("HIT", mu.inter().label(), 0, null, false));
        if (mu.inter().result() == -1) adv++;
        String label = switch (reaction) {
            case DEFEND -> "Defender";
            case DODGE -> "Esquiva";
            default -> "Contra-ataque";
        };
        Calc rc = new Calc(RollSourceType.ATTRIBUTE_TEST, null, label, null, null, 0, RollRules.CRIT_MARGIN, 0, hit,
                null, List.of());
        return rolls.rollCalc(tsh, e.getCampaignId(), rc, adv, label + ": " + tp.getName() + " vs " + att.getName(),
                userId, role);
    }

    // ---------- Contra-Ataque Tardio (RN-REA-04) ----------

    public AttackResponse lateCounter(UUID attackId, UUID targetRowId, LateCounterRequest req, UUID userId) {
        PendingAttack a = attacks.findById(attackId).orElseThrow(() -> new EncounterNotFoundException("Ataque não encontrado"));
        Encounter e = load(a.getEncounterId());
        boolean master = requireMembership(e.getCampaignId(), userId) == Role.MASTER;
        ActorRole role = master ? ActorRole.MASTER : ActorRole.PLAYER;
        if (e.getStatus() != EncounterStatus.ACTIVE) throw new CombatConflictException("O encontro não está em andamento");
        Ctx ctx = new Ctx(e);
        List<AttackTarget> ts = targets.findByAttackIdIn(List.of(a.getId()));
        AttackTarget t = ts.stream().filter(x -> x.getId().equals(targetRowId)).findFirst()
                .orElseThrow(() -> new EncounterNotFoundException("Alvo não encontrado"));
        EncounterParticipant tp = ctx.participant(t.getTargetId());
        CharacterSheet tsh = ctx.sheetOf.get(tp.getId());
        if (!master && !userId.equals(ownerUserId(tsh))) {
            throw new AccessDeniedException("Apenas o dono do alvo ou o Mestre pode contra-atacar");
        }
        if (t.getLateCounter() != null) throw new CombatConflictException("Este alvo já usou o Contra-Ataque Tardio");
        if (a.getStatus() != AttackStatus.RESOLVED || a.getResolvedTurn() == null
                || a.getResolvedTurn() != e.getTurnCounter()) {
            throw new CombatConflictException("A janela do Contra-Ataque Tardio já fechou");
        }
        if (t.getOutcome() != AttackOutcome.DEFENDED && t.getOutcome() != AttackOutcome.DODGED) {
            throw new InvalidCombatException("O Contra-Ataque Tardio exige Defender ou Esquiva bem-sucedidos");
        }
        if (ctx.hasAny(tp.getId(), Set.of("INCONSCIENTE", "MORTO"))) {
            throw new InvalidCombatException(tp.getName() + " está fora de combate e não pode agir");
        }
        if (req.sourceType() != RollSourceType.TECHNIQUE && req.sourceType() != RollSourceType.BASIC_ATTACK) {
            throw new InvalidCombatException("Fonte de ataque inválida");
        }
        EncounterParticipant att = ctx.participant(a.getAttackerId());
        CharacterSheet attSheet = ctx.sheetOf.get(att.getId());
        PendingAttack probe = PendingAttack.builder().sourceType(req.sourceType()).sourceId(req.sourceId())
                .variation(req.variation()).basicAttack(req.basicAttack()).build();
        boolean downed = ctx.has(tp.getId(), KNOCKED_DOWN);
        Calc calc = calcFor(tsh, probe, downed, tp, tsh);
        CharacterTechnique tech = req.sourceType() == RollSourceType.TECHNIQUE ? technique(tsh, probe) : null;
        calc = withMartial(calc, probe, tp, tsh, downed, martial.fighter(tp, tsh), tech, Inter.NONE);
        int sp = req.sourceType() == RollSourceType.BASIC_ATTACK
                ? VitalRules.adjustSp(1, VitalRules.tier(tp.getAdrenaline()), ctx.has(tp.getId(), "DISSOCIADO"))
                : calc.spCost();
        if (sp > 0 && ctx.has(tp.getId(), "EXAUSTO")) {
            throw new InvalidCombatException("Exausto: sem técnicas nem ações com custo de SP");
        }
        if (tsh.getCurrentSp() < sp) {
            throw new InvalidCombatException("SP insuficiente: custa " + sp + " e restam " + tsh.getCurrentSp());
        }
        vital.applySp(e, tp, tsh, -sp, VitalService.Ctx.of(userId, role, "Contra-ataque tardio"));
        RollMessage original = rolls.message(a.getAttackRollId());
        RollMessage msg = rolls.rollCalc(tsh, e.getCampaignId(), calc, ctx.has(tp.getId(), "DISSOCIADO") ? -1 : 0,
                "Contra-ataque tardio: " + tp.getName() + " → " + att.getName(), userId, role);
        if (msg.total() > original.total()) {
            int raw = msg.damageTotal() == null ? 0 : msg.damageTotal();
            int dmg = raw <= 0 ? 0 : AttackRules.damage(raw, 0, profiles.calejo(attSheet, att.getActiveBaseId()));
            attacksAttacker(e, att, attSheet, dmg, new VitalService.Ctx(userId, role,
                    "Contra-ataque tardio de " + tp.getName(), msg.critical()));
            t.setLateCounter(LateCounter.SUCCESS);
            t.setLateCounterDamage(dmg);
            if (dmg > 0) {
                martial.fire(e, tp, tsh, "HIT", att.getId(), null);
                martial.fire(e, att, attSheet, "GOT_HIT", tp.getId(), null);
            }
            log(e, tp.getId(), tp.getName() + " contra-atacou tarde " + att.getName() + ": " + dmg + " de dano");
        } else if (tech != null && profiles.hasModule(tsh, tech, req.variation(), "Resposta Segura")) {
            t.setLateCounter(LateCounter.SAFE);
            t.setLateCounterDamage(0);
            log(e, tp.getId(), tp.getName() + " falhou no contra-ataque tardio (Resposta Segura: sem punição)");
        } else {
            // a reação vira falha crítica: dano integral do ataque original, sem a mitigação da reação
            int full = original.damageTotal() == null || original.damageTotal() <= 0 ? 0
                    : AttackRules.damage(original.damageTotal(), 0, profiles.calejo(tsh, tp.getActiveBaseId()));
            int already = t.getDamage() == null ? 0 : t.getDamage();
            int extra = Math.max(0, full - already);
            if (extra > 0) {
                vital.applyHp(e, tp, tsh, -extra, new VitalService.Ctx(userId, role,
                        "Contra-ataque tardio falhou: " + a.getSourceName(), original.critical()));
            }
            t.setDamage(already + extra);
            t.setOutcome(AttackOutcome.HIT);
            t.setMitigationPct(0);
            t.setLateCounter(LateCounter.FAIL);
            t.setLateCounterDamage(extra);
            log(e, tp.getId(), tp.getName() + " falhou no contra-ataque tardio e recebeu " + extra + " de dano extra");
        }
        targets.save(t);
        return done(e, a, ts, ctx, userId, master);
    }

    // ---------- helpers ----------

    private CharacterTechnique technique(CharacterSheet s, PendingAttack a) {
        return technique(s, a.getSourceId());
    }

    private CharacterTechnique technique(CharacterSheet s, UUID sourceId) {
        if (sourceId == null) throw new InvalidCombatException("Informe a técnica");
        return techniques.findById(sourceId)
                .filter(t -> t.getSheet().getId().equals(s.getId()) && t.getState() == TechniqueState.ACTIVE)
                .orElseThrow(() -> new InvalidCombatException("Técnica não encontrada ou inativa"));
    }

    /**
     * Crítico Garantido (capítulo de Condições) = **dano** crítico no alvo, não acerto crítico: alvo Inconsciente que
     * não seja por HP e por quem ninguém reagiu, ou Ataque Localizado na região de um ferimento permanente não tratado.
     * Null = não garantido.
     */
    private String guaranteedCrit(PendingAttack a, AttackTarget t, EncounterParticipant tp, CharacterSheet tsh) {
        boolean out = conditions.findByParticipantIdIn(List.of(tp.getId())).stream()
                .anyMatch(c -> VitalService.UNCONSCIOUS.equals(c.getConditionKey()) && !"HP".equals(c.getNote()));
        if (out && t.getReaction() == ReactionType.NONE) return "alvo inconsciente"; // só se ninguém reagiu
        if (a.getRegion() != null && tsh != null && tsh.getInjuries().stream()
                .anyMatch(i -> !i.isTreated() && VitalRules.sameRegion(a.getRegion(), i.getRegion()))) {
            return "ferimento não tratado";
        }
        return null;
    }

    /**
     * Aliados que podem reagir por um alvo Inconsciente: mesmo time, outro lutador, não é o atacante, consciente e vivo.
     * Adjacência não é verificada (sem grade).
     */
    private List<EncounterParticipant> guardians(EncounterParticipant tp, EncounterParticipant att, Ctx ctx) {
        return ctx.ordered.stream()
                .filter(g -> g.getTeam() == tp.getTeam() && !g.getId().equals(tp.getId())
                        && (att == null || !g.getId().equals(att.getId()))
                        && !ctx.hasAny(g.getId(), Set.of(VitalService.UNCONSCIOUS, "MORTO")))
                .toList();
    }

    private Matchup guardianMatchup(MartialService.Fighter fa, CharacterTechnique tech, EncounterParticipant att,
                                    CharacterSheet attSheet, EncounterParticipant gp, CharacterSheet gsh) {
        MartialService.Fighter fd = martial.fighter(gp, gsh);
        return new Matchup(fd, interaction(fa, tech, att, attSheet, fd, gp, gsh));
    }

    /** Tipo marcial do golpe: arte da técnica, senão a arte principal (escopo dos itens de base, §31). */
    private MartialType attackTypeOf(PendingAttack a, EncounterParticipant att, CharacterSheet attSheet) {
        CharacterTechnique t = a.getSourceType() == RollSourceType.TECHNIQUE ? technique(attSheet, a) : null;
        return martial.attackType(martial.fighter(att, attSheet), t);
    }

    private Calc calcFor(CharacterSheet s, PendingAttack a, boolean downed, EncounterParticipant att, CharacterSheet attSheet) {
        var o = new RollProfileService.Opts(martial.validBase(attSheet, att.getActiveBaseId()), downed, a.getRegion(),
                a.getRegionPenalty(), attackTypeOf(a, att, attSheet));
        Calc c;
        if (a.getSourceType() == RollSourceType.TECHNIQUE) c = profiles.technique(s, technique(s, a), a.getVariation(), o);
        else {
            if (a.getBasicAttack() == null) throw new InvalidCombatException("Informe o ataque básico");
            c = profiles.basic(s, a.getBasicAttack(), o);
        }
        return withAdrenaline(c, att, attSheet);
    }

    /** Parcelas nomeadas de Adrenalina/Histeria/Dissociação no Calc (F6c). */
    private Calc withAdrenaline(Calc c, EncounterParticipant att, CharacterSheet s) {
        VitalRules.Tier tier = VitalRules.tier(att.getAdrenaline());
        boolean dissociated = VitalRules.dissociated(s.getMentalFatigue());
        List<RollPart> hit = new ArrayList<>(c.hit());
        List<RollPart> damage = new ArrayList<>(c.damage());
        Hysteria h = VitalRules.activeHysteria(att.getAdrenaline(), att.getHysteria());
        if (h == Hysteria.FURY) {
            hit.add(new RollPart("HIT", "Fúria", 4, null, false));
            int face = DICE.nextInt(6) + 1; // +1d6, não dobra no crítico
            damage.add(new RollPart("DAMAGE", "Fúria +1d6", face, List.of(face), false));
        }
        if (h == Hysteria.LIGHTNING) hit.add(new RollPart("HIT", "Relâmpago", 2, null, false));
        if (tier != VitalRules.Tier.NONE) hit.add(new RollPart("HIT", "Adrenalina: Fluxo −1 SP", 0, null, false));
        if (dissociated) hit.add(new RollPart("HIT", "Dissociação: SP ×2, −1 vantagem", 0, null, false));
        int sp = c.type() == RollSourceType.BASIC_ATTACK ? c.spCost()
                : VitalRules.adjustSp(c.spCost(), tier, dissociated);
        return new Calc(c.type(), c.sourceId(), c.name(), c.variation(), c.basic(), sp,
                VitalRules.critMargin(tier), c.advantage(), hit, c.dice(), damage);
    }

    private static int hysteriaReaction(EncounterParticipant tp) {
        Hysteria h = VitalRules.activeHysteria(tp.getAdrenaline(), tp.getHysteria());
        return h == Hysteria.WALL ? 4 : h == Hysteria.FURY ? -2 : h == Hysteria.LIGHTNING ? 2 : 0;
    }

    private int headBonus(CharacterSheet attSheet, PendingAttack a) {
        if (a.getSourceType() != RollSourceType.TECHNIQUE || VitalRules.normalizeRegion(a.getRegion()).isEmpty()) return 0;
        return profiles.headImpactAndPower(attSheet, technique(attSheet, a), a.getVariation());
    }

    private void attacksAttacker(Encounter e, EncounterParticipant att, CharacterSheet attSheet, int dmg,
                                 VitalService.Ctx c) {
        if (dmg > 0) vital.applyHp(e, att, attSheet, -dmg, c);
    }

    /** Sem tipo de dano no modelo: crítico com dado de dano no máximo vira aviso ao Mestre. */
    /** Ferimento permanente: crítico em que algum dado de dano do golpe tirou o máximo (dados extras não contam). */
    private static boolean permanentInjury(RollMessage atk, Calc calc) {
        if (!atk.critical() || calc.dice() == null || atk.breakdown() == null) return false;
        return atk.breakdown().stream().anyMatch(p -> "DAMAGE".equals(p.part()) && p.dice() != null
                && !p.source().startsWith("Fúria") && !p.source().startsWith("Guarda")
                && p.dice().stream().anyMatch(d -> d == calc.dice().sides()));
    }

    /**
     * Ataques RESOLVED da mesma fonte contra este alvo, incluindo o atual (Timing). A troca de guarda de qualquer
     * dos dois reinicia a contagem: só valem ataques resolvidos depois dela.
     */
    private int uses(PendingAttack current, EncounterParticipant att, EncounterParticipant tp) {
        Instant cutoff = att.getGuardChangedAt();
        if (tp.getGuardChangedAt() != null && (cutoff == null || tp.getGuardChangedAt().isAfter(cutoff))) {
            cutoff = tp.getGuardChangedAt();
        }
        Instant since = cutoff;
        List<PendingAttack> prior = attacks.findByEncounterIdAndAttackerIdAndStatus(current.getEncounterId(),
                current.getAttackerId(), AttackStatus.RESOLVED).stream()
                .filter(p -> !p.getId().equals(current.getId()) && p.getSourceType() == current.getSourceType()
                        && java.util.Objects.equals(p.getSourceId(), current.getSourceId())
                        && p.getBasicAttack() == current.getBasicAttack()
                        && java.util.Objects.equals(p.getVariation(), current.getVariation())
                        && (since == null || (p.getResolvedAt() != null && p.getResolvedAt().isAfter(since))))
                .toList();
        if (prior.isEmpty()) return 1;
        Set<UUID> ids = prior.stream().map(PendingAttack::getId).collect(Collectors.toSet());
        return 1 + (int) targets.findByAttackIdIn(ids).stream().filter(t -> t.getTargetId().equals(tp.getId())).count();
    }

    private static int countAttackers(EncounterParticipant tp, UUID attackerId) {
        List<UUID> l = tp.getAttackersThisRound() == null ? List.of() : tp.getAttackersThisRound();
        return l.size() + (l.contains(attackerId) ? 0 : 1);
    }

    private static String names(Ctx ctx, List<AttackTarget> ts) {
        return ts.stream().map(t -> ctx.participant(t.getTargetId()).getName()).collect(Collectors.joining(", "));
    }

    private static String summary(EncounterParticipant att, EncounterParticipant tp, PendingAttack a, AttackTarget t,
                                  int counter) {
        String how = switch (t.getReaction()) {
            case DEFEND -> t.getOutcome() == AttackOutcome.DEFENDED ? "defendeu " + t.getMitigationPct() + "%"
                    : "defesa falhou, " + t.getMitigationPct() + "% mitigado";
            case DODGE -> "esquiva falhou";
            case COUNTER -> "contra-ataque falhou";
            case NONE -> "sem reação";
        };
        String with = " com " + a.getSourceName();
        return switch (t.getOutcome()) {
            case MISS -> att.getName() + " errou " + tp.getName() + with + " (falha crítica)";
            case DODGED -> tp.getName() + " esquivou de " + att.getName() + " (" + a.getSourceName() + ")";
            case COUNTERED -> tp.getName() + " contra-atacou " + att.getName() + ": " + counter + " de dano em "
                    + att.getName() + (t.getDamage() > 0 ? ", recebeu " + t.getDamage() : ", sem receber dano");
            default -> att.getName() + " acertou " + tp.getName() + with + ": " + t.getDamage() + " de dano (" + how + ")";
        };
    }

    private void log(Encounter e, UUID participantId, String text) {
        timeline.save(CombatEvent.builder().encounterId(e.getId()).round(e.getRound()).turnCounter(e.getTurnCounter())
                .participantId(participantId).type(CombatEventType.ACTION)
                .text(text.length() > 500 ? text.substring(0, 500) : text).build());
    }
}
