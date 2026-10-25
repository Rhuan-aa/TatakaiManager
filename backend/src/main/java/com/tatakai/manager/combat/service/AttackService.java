package com.tatakai.manager.combat.service;

import com.tatakai.manager.combat.dto.response.AttackResponse.Option;
import com.tatakai.manager.combat.dto.response.AttackResponse.TargetResponse;
import com.tatakai.manager.roll.service.RollProfileService.Calc;

import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.catalog.entity.AssemblyItem;
import com.tatakai.manager.catalog.entity.AssemblyItemKind;
import com.tatakai.manager.catalog.entity.MartialType;
import com.tatakai.manager.catalog.entity.TechniqueCore;
import com.tatakai.manager.catalog.service.EquipmentCatalog;
import com.tatakai.manager.catalog.service.WeaponCatalog;
import com.tatakai.manager.catalog.service.rules.ModuleRules;
import com.tatakai.manager.combat.dto.request.CreateAttackRequest;
import com.tatakai.manager.combat.dto.request.LateCounterRequest;
import com.tatakai.manager.combat.dto.response.AttackResponse;
import com.tatakai.manager.combat.dto.response.CombatUpdateMessage;
import com.tatakai.manager.combat.entity.AttackActionKind;
import com.tatakai.manager.combat.entity.AttackOutcome;
import com.tatakai.manager.combat.entity.AttackStatus;
import com.tatakai.manager.combat.entity.AttackTarget;
import com.tatakai.manager.combat.entity.BasicAttack;
import com.tatakai.manager.combat.entity.CombatCondition;
import com.tatakai.manager.combat.entity.CombatEvent;
import com.tatakai.manager.combat.entity.CombatEventType;
import com.tatakai.manager.combat.entity.ConditionExpiry;
import com.tatakai.manager.combat.entity.Encounter;
import com.tatakai.manager.combat.entity.EncounterParticipant;
import com.tatakai.manager.combat.entity.EncounterStatus;
import com.tatakai.manager.combat.entity.Guard;
import com.tatakai.manager.combat.entity.Hysteria;
import com.tatakai.manager.combat.entity.LateCounter;
import com.tatakai.manager.combat.entity.PendingAttack;
import com.tatakai.manager.combat.entity.ReactionType;
import com.tatakai.manager.combat.entity.StrikeSide;
import com.tatakai.manager.combat.exception.CombatConflictException;
import com.tatakai.manager.combat.exception.EncounterNotFoundException;
import com.tatakai.manager.combat.exception.InvalidCombatException;
import com.tatakai.manager.combat.repository.AttackTargetRepository;
import com.tatakai.manager.combat.repository.CombatConditionRepository;
import com.tatakai.manager.combat.repository.CombatEventRepository;
import com.tatakai.manager.combat.repository.EncounterParticipantRepository;
import com.tatakai.manager.combat.repository.EncounterRepository;
import com.tatakai.manager.combat.repository.PendingAttackRepository;
import com.tatakai.manager.combat.service.rules.AttackRules;
import com.tatakai.manager.combat.service.rules.BaseRules;
import com.tatakai.manager.combat.service.rules.CombatRules;
import com.tatakai.manager.combat.service.rules.EffectRules;
import com.tatakai.manager.combat.service.rules.EquipmentRules;
import com.tatakai.manager.combat.service.rules.GrappleRules;
import com.tatakai.manager.combat.service.rules.MartialRules;
import com.tatakai.manager.combat.service.rules.TechniqueRules;
import com.tatakai.manager.combat.service.rules.VitalRules;
import com.tatakai.manager.combat.service.rules.WeaponRules;
import com.tatakai.manager.martial.entity.CharacterTechnique;
import com.tatakai.manager.martial.entity.TechniqueState;
import com.tatakai.manager.martial.repository.CharacterTechniqueRepository;
import com.tatakai.manager.roll.dto.response.RollMessage;
import com.tatakai.manager.roll.entity.RollPart;
import com.tatakai.manager.roll.entity.RollSourceType;
import com.tatakai.manager.roll.service.RollProfileService;
import com.tatakai.manager.roll.service.RollService;
import com.tatakai.manager.roll.service.rules.RollRules;
import com.tatakai.manager.shared.exception.AccessDeniedException;
import com.tatakai.manager.sheet.entity.ActorRole;
import com.tatakai.manager.sheet.entity.Attribute;
import com.tatakai.manager.sheet.entity.CharacterSheet;
import com.tatakai.manager.sheet.entity.Dominance;
import com.tatakai.manager.sheet.entity.InventoryItem;
import com.tatakai.manager.sheet.entity.ItemLocation;
import com.tatakai.manager.sheet.repository.CharacterSheetRepository;
import com.tatakai.manager.sheet.service.SheetActivityLogService;
import com.tatakai.manager.sheet.service.rules.SheetRules;

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
    /** Arma Pesada: Esquiva com desvantagem até o início do próximo turno de quem atacou (§42). */
    private static final String ARC_WEIGHT = "PESO_DO_ARCO";
    /** Nota do Agarrado por arma de Enredar (sem ninguém segurando). */
    public static final String ENTANGLED = "ENREDAR";
    /** Catálogos estáticos de armas e equipamentos (só leitura do classpath). */
    private final WeaponCatalog weapons = new WeaponCatalog();
    private final EquipmentCatalog equipment = new EquipmentCatalog();
    /** Mordida: quem agarra o mordedor faz Mental contra este ND ou solta (§40). */
    private static final int BITE_ND = 12;
    /** Regiões da face para a Cabeçada (−2 PA no próximo turno do alvo, §40). */
    private static final Set<String> FACE = Set.of("face", "rosto", "nariz");
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
                .grip(req.basicAttack() == BasicAttack.AGARRAO ? (req.grip() == null ? GrappleRules.Grip.ARM : req.grip()) : null)
                .squeeze(Boolean.TRUE.equals(req.squeeze()))
                .runMeters(req.runMeters()).surface(Boolean.TRUE.equals(req.surface())).distanceM(req.distanceM())
                .status(AttackStatus.AWAITING_REACTIONS).round(e.getRound()).sourceName("").build();
        if (ctx.hasAny(att.getId(), Set.of("INCONSCIENTE", "MORTO"))) {
            throw new InvalidCombatException(att.getName() + " está fora de combate e não pode agir");
        }
        if (ctx.has(att.getId(), PARALYZED)) throw new InvalidCombatException(att.getName() + " está Paralisado e não pode agir");
        if (req.basicAttack() == BasicAttack.CHUTE && ctx.has(att.getId(), GrappleRules.CLINCH)) {
            throw new InvalidCombatException("No Clinch não cabem golpes de alcance Médio (Chute)");
        }
        if (req.basicAttack() == BasicAttack.AGARRAO && !RollProfileService.inHand(s).isEmpty()) {
            throw new InvalidCombatException("O Agarrão exige as duas mãos livres");
        }
        if (req.basicAttack() == BasicAttack.PISAO && targetIds.stream().anyMatch(t -> !ctx.has(t, KNOCKED_DOWN))) {
            throw new InvalidCombatException("Pisão só contra alvo Derrubado");
        }
        Calc calc = calcFor(s, a, ctx.has(att.getId(), KNOCKED_DOWN), att, s);
        a.setVariation(calc.variation());
        String name = calc.variation() == null ? calc.name() : calc.name() + " (" + calc.variation() + ")";
        a.setSourceName(name.length() > 100 ? name.substring(0, 100) : name);
        checkModules(e, a, att, s, calc.weapon() != null && calc.weapon().area() ? 0 : targetIds.size());
        InventoryItem gun = weaponItem(s, a, calc);

        boolean slow = false;
        boolean quick = a.getBasicAttack() != null && (a.getBasicAttack().quick()
                || (a.getBasicAttack() == BasicAttack.ARMA && "QUICK".equals(a.getVariation()))); // Golpe Rápido (Leve)
        if (a.getSourceType() == RollSourceType.TECHNIQUE) {
            CharacterTechnique t = technique(s, a);
            slow = profiles.hasModule(s, t, a.getVariation(), "Lenta");
            quick = profiles.hasModule(s, t, a.getVariation(), "Técnica Rápida");
        }
        if (a.getActionKind() == AttackActionKind.MINOR_OFFENSIVE && ctx.has(att.getId(), "DISSOCIADO")) {
            throw new InvalidCombatException("Dissociado: Ações Menores estão bloqueadas");
        }
        if (a.getActionKind() == AttackActionKind.MINOR_OFFENSIVE && !quick) {
            throw new InvalidCombatException("Ação Menor Ofensiva exige Técnica Rápida, Soco, Mordida, Empurrão ou Golpe Rápido de arma Leve");
        }
        int pa = AttackRules.paCost(a.getActionKind(), slow);
        int sp = a.getSourceType() == RollSourceType.BASIC_ATTACK
                ? VitalRules.adjustSp(1, VitalRules.tier(att.getAdrenaline()), ctx.has(att.getId(), "DISSOCIADO"))
                : calc.spCost();
        if (a.getActionKind() == AttackActionKind.MAJOR) sp += EquipmentRules.majorSp(armorWeight(s)); // Proteção Pesada
        BaseRules.Profile ownBase = profiles.baseProfile(s, att.getActiveBaseId());
        if (a.getSourceType() == RollSourceType.TECHNIQUE && ownBase != null && ownBase.economy(attackTypeOf(a, att, s))) {
            sp = Math.max(0, sp - 1); // Economia da base: fixo, depois dos multiplicadores
        }
        MartialService.Fighter faDecl = martial.fighter(att, s);
        if (a.getSourceType() == RollSourceType.TECHNIQUE) {
            CharacterTechnique tDecl = technique(s, a);
            String artDecl = techArtName(tDecl);
            if ("Capoeira".equalsIgnoreCase(artDecl) && faDecl.hasArtNamed("Capoeira")
                    && martial.counterValue(att.getId(), "RITMO", null) >= 5) {
                sp = Math.max(1, sp / 2); // Ritmo Pleno (5 PR): técnicas de Capoeira custam metade do SP (mín. 1)
            }
            if ("Judô".equalsIgnoreCase(artDecl) && faDecl.hasArtNamed("Judô")
                    && profiles.hasModule(s, tDecl, a.getVariation(), "Derrubar")
                    && ctx.has(targetIds.get(0), "DESEQUILIBRADO")) {
                CharacterSheet ts0 = ctx.sheetOf.get(targetIds.get(0));
                if (ts0 != null) sp = Math.max(1, sp - Math.max(0, RollRules.modOrZero(ts0.getStr()))); // Kuzushi: Mínimo Esforço
            }
        }
        if (sp > 0 && ctx.has(att.getId(), "EXAUSTO")) {
            throw new InvalidCombatException("Exausto: sem técnicas nem ações com custo de SP");
        }
        if (att.getPaCurrent() < pa) {
            throw new InvalidCombatException("PA insuficiente: custa " + pa + " e restam " + att.getPaCurrent());
        }
        if (s.getCurrentSp() < sp) {
            throw new InvalidCombatException("SP insuficiente: custa " + sp + " e restam " + s.getCurrentSp());
        }
        fireCheck(s, a, calc, gun, calc.weapon() != null && extendedShot(s, a));
        ActorRole role = master ? ActorRole.MASTER : ActorRole.PLAYER;
        att.setPaCurrent(att.getPaCurrent() - pa);
        att.setEngagedThisRound(true);
        consumeShot(e, att, s, a, calc, gun);
        vital.applySp(e, att, s, -sp, VitalService.Ctx.of(userId, role, "Ataque: " + a.getSourceName()));
        a.setReservedPa(pa);
        a.setReservedSp(sp);
        a = attacks.save(a);
        if (faDecl.hasArtNamed("Entertainment Wrestling")
                && targetIds.stream().anyMatch(tid -> ctx.hasAny(tid, Set.of(KNOCKED_DOWN, "VULNERAVEL")))) {
            martial.addCounter(e, att, s, "POP", null, 1);
        }

        List<AttackTarget> ts = openTargets(a, targetIds, att, ctx);
        log(e, att.getId(), att.getName() + " declara " + a.getSourceName() + " contra "
                + names(ctx, ts) + " (" + pa + " PA" + (sp > 0 ? ", " + sp + " SP" : "") + ")");
        if (ts.stream().allMatch(t -> t.getReaction() != null)) resolve(e, a, ts, ctx, userId, role);
        return done(e, a, ts, ctx, userId, master);
    }

    /** Linhas de alvo do ataque; quem não pode reagir (Inconsciente sem aliado, Vulnerável…) já fica com NONE. */
    private List<AttackTarget> openTargets(PendingAttack a, List<UUID> targetIds, EncounterParticipant att, Ctx ctx) {
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
        return ts;
    }

    /**
     * Módulos e limitações que valem na declaração (§41): número de alvos (Varredura/Círculo), Tardia, Uso Restrito e
     * Exige Base. Ataque básico: 1 alvo.
     */
    /** `targetCount` 0 = sem limite (armas de área). */
    private void checkModules(Encounter e, PendingAttack a, EncounterParticipant att, CharacterSheet s, int targetCount) {
        List<AssemblyItem> items = a.getSourceType() == RollSourceType.TECHNIQUE
                ? profiles.items(s, technique(s, a), a.getVariation()) : List.of();
        int max = ModuleRules.maxTargets(items);
        if (targetCount > max) {
            String multi = ModuleRules.has(items, "Supressão") ? "Supressão"
                    : ModuleRules.has(items, "Transpassar") ? "Transpassar" : "Varredura";
            throw new InvalidCombatException(max == ModuleRules.SINGLE ? "Este golpe atinge um alvo só (Varredura: 2; Círculo: todos)"
                    : multi + " atinge no máximo " + max + " alvos");
        }
        if (items.isEmpty()) return;
        int min = ModuleRules.minRound(items);
        if (e.getRound() < min) throw new InvalidCombatException("Tardia: só a partir da " + min + "ª rodada");
        if (ModuleRules.has(items, "Uso Restrito") && attacks.findByEncounterIdOrderByCreatedAtDesc(e.getId()).stream()
                .anyMatch(x -> x.getAttackerId().equals(att.getId()) && x.getStatus() != AttackStatus.CANCELLED
                        && x.getSourceType() == RollSourceType.TECHNIQUE && a.getSourceId().equals(x.getSourceId()))) {
            throw new InvalidCombatException("Uso Restrito: a técnica já foi usada neste combate");
        }
        String need = ModuleRules.requiredBase(items);
        if (need != null) {
            CharacterTechnique base = martial.baseOrNull(s, att.getActiveBaseId());
            if (base == null) throw new InvalidCombatException("Exige Base: entre " + (need.isEmpty() ? "em uma base" : "na base " + need));
            if (!need.isEmpty() && !VitalRules.normalizeRegion(base.getName()).contains(VitalRules.normalizeRegion(need))) {
                throw new InvalidCombatException("Exige Base: só na base " + need);
            }
        }
    }

    // ---------- reagir ----------

    public AttackResponse respond(UUID attackId, UUID targetRowId, ReactionType reaction, UUID userId) {
        return respond(attackId, targetRowId, reaction, null, null, null, userId);
    }

    public AttackResponse respond(UUID attackId, UUID targetRowId, ReactionType reaction, UUID defenderId, UUID userId) {
        return respond(attackId, targetRowId, reaction, defenderId, null, null, userId);
    }

    public AttackResponse respond(UUID attackId, UUID targetRowId, ReactionType reaction, UUID defenderId,
                                  UUID techniqueId, String variation, UUID userId) {
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
        if (techniqueId != null) {
            // técnica de Reação: a reação passa a ser a do tipo da técnica e o gasto é o SP dela
            RollProfileService.CoreTechnique rt = profiles.coreTechniques(ts0, TechniqueCore.REACTION).stream()
                    .filter(x -> x.technique().getId().equals(techniqueId) && java.util.Objects.equals(x.variation(), variation))
                    .findFirst().orElse(null);
            if (rt != null) {
                reaction = TechniqueRules.reaction(rt.items()).type();
            } else {
                // Golpe com Contragolpe (§41): vira o Contra-ataque, com o acerto, o dano e o gasto da técnica
                rt = counterStrikes(ts0).stream().filter(x -> x.technique().getId().equals(techniqueId)
                        && java.util.Objects.equals(x.variation(), variation)).findFirst()
                        .orElseThrow(() -> new InvalidCombatException("Técnica de Reação ou de Contragolpe não encontrada ou inativa"));
                reaction = ReactionType.COUNTER;
            }
            String why = techReason(ctx, t.getTargetId(), ts0, rt);
            if (why != null) throw new InvalidCombatException(why);
            t.setReactionTechniqueId(techniqueId);
            t.setReactionVariation(variation);
        }
        if (reaction == ReactionType.DODGE && ctx.has(t.getTargetId(), RESTRAINED)) {
            throw new InvalidCombatException("Imobilizado não pode Esquivar");
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

    // ---------- encadear (§41) ----------

    /**
     * Encadear: com o golpe ainda aguardando reações, o atacante aborta (sem reembolso) e usa outra técnica ATIVA do
     * mesmo núcleo contra os mesmos alvos, sem PA, pagando o gasto dela +25%. As reações recomeçam.
     */
    public AttackResponse chain(UUID attackId, UUID techniqueId, String variation, UUID userId) {
        PendingAttack a = attacks.findById(attackId).orElseThrow(() -> new EncounterNotFoundException("Ataque não encontrado"));
        Encounter e = load(a.getEncounterId());
        boolean master = requireMembership(e.getCampaignId(), userId) == Role.MASTER;
        ActorRole role = master ? ActorRole.MASTER : ActorRole.PLAYER;
        Ctx ctx = new Ctx(e);
        EncounterParticipant att = ctx.participant(a.getAttackerId());
        CharacterSheet s = ctx.sheetOf.get(att.getId());
        if (!master && !userId.equals(ownerUserId(s))) throw new AccessDeniedException("Apenas o atacante ou o Mestre pode encadear");
        if (a.getStatus() != AttackStatus.AWAITING_REACTIONS) throw new CombatConflictException("O golpe já foi resolvido");
        CharacterTechnique old = a.getSourceType() == RollSourceType.TECHNIQUE ? technique(s, a) : null;
        if (old == null || !profiles.hasModule(s, old, a.getVariation(), "Encadear")) {
            throw new InvalidCombatException("Só uma técnica com Encadear pode ser encadeada");
        }
        CharacterTechnique next = technique(s, techniqueId);
        if (next.getId().equals(old.getId())) throw new InvalidCombatException("Escolha outra técnica");
        TechniqueCore oc = profiles.core(s, old, a.getVariation());
        TechniqueCore nc = profiles.core(s, next, variation);
        if (nc == TechniqueCore.REACTION || nc == TechniqueCore.PREPARATION) {
            throw new InvalidCombatException("Encadear exige outra técnica do mesmo tipo");
        }
        MartialService.Fighter fa = martial.fighter(att, s);
        MartialType ot = martial.attackType(fa, old);
        MartialType nt = martial.attackType(fa, next);
        if (ot != nt || (ot == null && (oc == null ? TechniqueCore.STRIKE : oc) != (nc == null ? TechniqueCore.STRIKE : nc))) {
            throw new InvalidCombatException("Encadear exige outra técnica do mesmo tipo");
        }
        List<UUID> targetIds = targets.findByAttackIdIn(List.of(a.getId())).stream().map(AttackTarget::getTargetId).toList();
        PendingAttack n = PendingAttack.builder().encounterId(e.getId()).attackerId(att.getId())
                .sourceType(RollSourceType.TECHNIQUE).sourceId(next.getId()).variation(variation)
                .actionKind(a.getActionKind()).region(a.getRegion()).regionPenalty(a.getRegionPenalty())
                .strikeSide(a.getStrikeSide()).runMeters(a.getRunMeters()).surface(a.isSurface())
                .status(AttackStatus.AWAITING_REACTIONS).round(e.getRound()).sourceName("").build();
        Calc calc = calcFor(s, n, ctx.has(att.getId(), KNOCKED_DOWN), att, s);
        n.setVariation(calc.variation());
        String name = calc.variation() == null ? calc.name() : calc.name() + " (" + calc.variation() + ")";
        n.setSourceName(name.length() > 100 ? name.substring(0, 100) : name);
        checkModules(e, n, att, s, targetIds.size());
        int sp = ModuleRules.chainSp(calc.spCost());
        if (sp > 0 && ctx.has(att.getId(), "EXAUSTO")) throw new InvalidCombatException("Exausto: sem técnicas");
        if (s.getCurrentSp() < sp) throw new InvalidCombatException("SP insuficiente: custa " + sp + " e restam " + s.getCurrentSp());
        a.setStatus(AttackStatus.CANCELLED);
        attacks.save(a);
        vital.applySp(e, att, s, -sp, VitalService.Ctx.of(userId, role, "Encadear: " + n.getSourceName()));
        n.setReservedPa(0);
        n.setReservedSp(sp);
        n = attacks.save(n);
        List<AttackTarget> ts = openTargets(n, targetIds, att, ctx);
        log(e, att.getId(), att.getName() + " aborta " + a.getSourceName() + " e encadeia " + n.getSourceName()
                + " (" + sp + " SP)");
        if (ts.stream().allMatch(t -> t.getReaction() != null)) resolve(e, n, ts, ctx, userId, role);
        return done(e, n, ts, ctx, userId, master);
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
            List<AttackResponse.TechOption> techs = can && guards == null ? techOptions(a, att, tp, tsh, ctx) : null;
            RollProfileService.CoreTechnique used = t.getReactionTechniqueId() == null ? null
                    : reactionTechnique(ctx.sheetOf.get(dp == null ? t.getTargetId() : dp.getId()), t);
            out.add(new TargetResponse(t.getId(), t.getTargetId(), tp == null ? null : tp.getName(), t.getReaction(),
                    t.isAutoResolved(), can, can && guards == null ? options(a, att, tp, tsh, ctx) : null,
                    t.getReactionRollId() == null ? null : rolls.message(t.getReactionRollId()), t.getOutcome(),
                    t.getMitigationPct(), t.getDamage(), t.getCounterDamage(), t.getLateCounter(),
                    t.getLateCounterDamage(), canLateCounter(a, t, ctx, tsh, userId, master),
                    t.getDefenderId(), dp == null ? null : dp.getName(), guards, techs, used == null ? null : used.name()));
        }
        return new AttackResponse(a.getId(), a.getEncounterId(), a.getAttackerId(), att == null ? null : att.getName(),
                a.getSourceName(), a.getActionKind(), a.getRegion(), a.getReservedPa(), a.getReservedSp(), a.getStatus(),
                a.getRound(), a.getAttackRollId() == null ? null : rolls.message(a.getAttackRollId()), out,
                canChain(a, att, ctx, userId, master));
    }

    private boolean canChain(PendingAttack a, EncounterParticipant att, Ctx ctx, UUID userId, boolean master) {
        if (a.getStatus() != AttackStatus.AWAITING_REACTIONS || a.getSourceType() != RollSourceType.TECHNIQUE || att == null) {
            return false;
        }
        CharacterSheet s = ctx.sheetOf.get(att.getId());
        if (s == null || !(master || userId.equals(ownerUserId(s)))) return false;
        return techniques.findById(a.getSourceId()).map(t -> profiles.hasModule(s, t, a.getVariation(), "Encadear")).orElse(false);
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
        boolean restrained = ctx.has(tp.getId(), RESTRAINED);
        BaseRules.Profile db = profiles.baseProfile(tsh, tp.getActiveBaseId());
        java.util.function.ToIntFunction<ReactionType> base = r -> db == null ? 0 : db.reaction(r);
        return List.of(
                new Option(ReactionType.DEFEND, true, null, RollRules.modOrZero(tsh.getCon()) + common + base.applyAsInt(ReactionType.DEFEND)),
                new Option(ReactionType.DODGE, !restrained, restrained ? "Imobilizado: não pode Esquivar" : null,
                        RollRules.modOrZero(tsh.getDex()) + common + base.applyAsInt(ReactionType.DODGE)),
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

    private Inter interaction(PendingAttack a, MartialService.Fighter fa, CharacterTechnique tech, EncounterParticipant att,
                              CharacterSheet attSheet, MartialService.Fighter fd, EncounterParticipant tp,
                              CharacterSheet tsh) {
        MartialType at = attackTypeOf(a, att, attSheet);
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
        List<AssemblyItem> items = tech == null ? List.of() : profiles.items(attSheet, tech, a.getVariation());

        MartialService.Fighter fa = martial.fighter(att, attSheet);
        Map<UUID, Matchup> matchups = new java.util.HashMap<>();
        for (AttackTarget t : ts) {
            EncounterParticipant tp = ctx.participant(t.getTargetId());
            CharacterSheet tsh = ctx.sheetOf.get(tp.getId());
            MartialService.Fighter fd = martial.fighter(tp, tsh);
            matchups.put(tp.getId(), new Matchup(fd, interaction(a, fa, tech, att, attSheet, fd, tp, tsh)));
        }
        calc = withMartial(e, calc, a, att, attSheet, downed, fa, tech, matchups.get(ts.get(0).getTargetId()).inter());
        calc = withSituation(calc, a, att, ctx.participant(ts.get(0).getTargetId()), ctx);
        calc = withModules(calc, a, items, ctx.participant(ts.get(0).getTargetId()));
        WeaponCatalog.Def weapon = calc.weapon();
        InventoryItem gun = weapon == null ? null : weaponItem(attSheet, a, calc);
        if (weapon != null && weapon.dispersion()) calc = calc.withDice(WeaponRules.dispersed(calc.dice(), a.getDistanceM()));

        boolean thrownTech = tech != null && ModuleRules.has(items, "Arremessar")
                && a.getDistanceM() != null && a.getDistanceM() > WeaponRules.CLOSE_M;
        int adv = (ctx.has(att.getId(), RESTRAINED) ? -1 : 0) + (ctx.has(att.getId(), DISORIENTED) ? -1 : 0)
                + (ctx.hasAny(ts.get(0).getTargetId(), EASY_TARGET) ? 1 : 0)
                + (ctx.has(att.getId(), "DISSOCIADO") ? -1 : 0)
                + (ctx.has(att.getId(), VitalService.BURNING) ? -1 : 0) // Queimando: desvantagem em precisão
                + rangeAdvantage(a, weapon, weapon != null && extendedShot(attSheet, a), thrownTech);
        String targetNames = names(ctx, ts);
        RollMessage atk = rolls.rollCalc(attSheet, e.getCampaignId(), calc, adv,
                "Ataque: " + att.getName() + " → " + targetNames, userId, role);
        a.setAttackRollId(atk.id());
        if (weapon != null && weapon.heavyStr() != null) { // o peso que terminou o arco ainda puxa o corpo
            vital.addEffectCondition(e, att, ARC_WEIGHT, ConditionExpiry.START_OF_TARGET_TURN, weapon.name(), null);
        }
        if (weapon != null && weapon.firearm() && WeaponRules.jams(atk.naturalD20(), weapon.unstable())) {
            jammed(e, a, ts, ctx, att, attSheet, gun, weapon, atk, vctx);
            return;
        }
        if (weapon != null && weapon.area() && atk.total() < WeaponRules.AREA_ND) {
            log(e, att.getId(), weapon.name() + " erra o ponto (" + atk.total() + " vs ND " + WeaponRules.AREA_ND
                    + "): cai 1d3 quadrados além, na direção que o Mestre escolher");
            ts.forEach(t -> { t.setOutcome(AttackOutcome.MISS); t.setMitigationPct(0); t.setDamage(0); t.setCounterDamage(0); targets.save(t); });
            finish(e, a);
            return;
        }
        String moveArt = tech == null || tech.getCharacterArt() == null ? null : tech.getCharacterArt().getArtName();
        boolean embalo5 = fa.hasArtNamed("Boxe")
                && (a.getBasicAttack() == BasicAttack.SOCO || "Boxe".equalsIgnoreCase(moveArt))
                && martial.counterValue(att.getId(), "EMBALO", null) >= 5;
        if (tech != null) martial.fire(e, att, attSheet, "ART_TECHNIQUE_USED", null, moveArt);
        int attackerCalejo = profiles.calejo(attSheet, att.getActiveBaseId());
        boolean pileOn = false;
        RollMessage second = null; // Sequência: 2º teste de acerto, rolado uma vez
        BaseRules.Profile attBase = profiles.baseProfile(attSheet, att.getActiveBaseId());
        Guard effGuard = MartialRules.effectiveGuard(att.getGuard(), downed);
        boolean tkdKick = fa.hasArtNamed("Taekwondo")
                && (a.getBasicAttack() == BasicAttack.CHUTE || "Taekwondo".equalsIgnoreCase(moveArt));

        for (AttackTarget t : ts) {
            EncounterParticipant tp = ctx.participant(t.getTargetId());
            CharacterSheet tsh = ctx.sheetOf.get(tp.getId());
            List<UUID> group = new ArrayList<>(tp.getAttackersThisRound() == null ? List.of() : tp.getAttackersThisRound());
            if (!group.contains(att.getId())) group.add(att.getId());
            tp.setAttackersThisRound(group);

            BaseRules.Profile tpBase = profiles.baseProfile(tsh, tp.getActiveBaseId());
            // quem reage: o próprio alvo ou o aliado que o defende (alvo Inconsciente)
            EncounterParticipant rp = t.getDefenderId() == null ? tp : ctx.participant(t.getDefenderId());
            CharacterSheet rsh = ctx.sheetOf.get(rp.getId());
            Matchup mu = rp == tp ? matchups.get(tp.getId()) : guardianMatchup(a, fa, tech, att, attSheet, rp, rsh);
            if (rp != tp) log(e, rp.getId(), rp.getName() + " reage no lugar de " + tp.getName());
            RollProfileService.CoreTechnique rt = reactionTechnique(rsh, t);
            TechniqueRules.Reaction rr = rt == null ? null : TechniqueRules.reaction(rt.items());
            if (rt != null) log(e, rp.getId(), rp.getName() + " reage com " + rt.name());
            RollProfileService.CoreTechnique counterTech = rt == null ? counterTech(rsh, t) : null;
            Calc counterCalc = rt == null ? counterCalc(rsh, rp, t, ctx) : null;
            if (counterCalc != null) log(e, rp.getId(), rp.getName() + " contra-ataca com " + counterCalc.name() + " (Contragolpe)");

            RollMessage rmsg = null;
            if (t.getReaction() != ReactionType.NONE && !atk.fumble() && ctx.has(rp.getId(), PARALYZED)
                    && !vital.willTest(e, rp, rsh, PARALYZED_REACTION_ND, "Paralisado: reagir",
                            VitalService.Ctx.of(userId, role, "Paralisado"))) {
                log(e, rp.getId(), rp.getName() + " não consegue reagir (Paralisado)");
                t.setReaction(ReactionType.NONE);
            }
            if (t.getReaction() != ReactionType.NONE && !atk.fumble()) {
                rmsg = reactionRoll(e, a, att, attSheet, reactionBoost(e, rp, rsh, rt, t.getReaction(), weapon, basicOf(a)), rp, rsh,
                        t.getReaction(), group.size(), ctx, mu, counterCalc, userId, role);
                t.setReactionRollId(rmsg.id());
                if (rt != null) {
                    vital.applySp(e, rp, rsh, -rt.spCost(), VitalService.Ctx.of(userId, role, "Técnica: " + rt.name()));
                } else if (counterCalc != null) {
                    vital.applySp(e, rp, rsh, -counterCalc.spCost(), VitalService.Ctx.of(userId, role, "Contragolpe: " + counterCalc.name()));
                } else if (t.getReaction() == ReactionType.COUNTER) {
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
            boolean reacted = r.outcome() == AttackOutcome.DEFENDED || r.outcome() == AttackOutcome.DODGED
                    || r.outcome() == AttackOutcome.COUNTERED;
            if (rr != null && rr.escalated() && t.getReaction() == ReactionType.DEFEND && r.mitigationPct() > 0) {
                r = new AttackRules.Result(r.outcome(), TechniqueRules.stepUp(r.mitigationPct()), r.counterHits());
            }
            if (rr != null && rr.interrupt() && reacted) {
                r = new AttackRules.Result(AttackOutcome.MISS, 100, false);
                log(e, rp.getId(), rp.getName() + " interrompe o golpe de " + att.getName());
            }
            // Atravessar: o golpe ignora a Redução de Dano do alvo; Preparando a Arma (Muay Thai): +3 RD contundente
            boolean atravessar = tech != null && profiles.hasModule(attSheet, tech, a.getVariation(), "Atravessar");
            boolean bluntHit = weapon == null || "BLUNT".equals(weapon.damageType());
            int calejo = atravessar ? 0 : profiles.calejo(tsh, tp.getActiveBaseId())
                    + (bluntHit && mu.defender().hasArtNamed("Muay Thai") ? 3 : 0);
            if (rr != null && rr.parry() && t.getReaction() == ReactionType.DEFEND && r.mitigationPct() > 0
                    && defenderWeapon(rsh) != null) { // Aparar: a redução sobe um grau
                r = new AttackRules.Result(r.outcome(), TechniqueRules.stepUp(r.mitigationPct()), r.counterHits());
            }
            if (tkdKick && effGuard == Guard.NATURAL && t.getReaction() == ReactionType.DEFEND && r.mitigationPct() > 0) {
                r = new AttackRules.Result(r.outcome(), TechniqueRules.stepDown(r.mitigationPct()), r.counterHits());
                log(e, tp.getId(), "Quebra-Escudo (Taekwondo): redução de Defender de " + rp.getName() + " cai para " + r.mitigationPct() + "%");
            }
            boolean areaDodge = weapon != null && weapon.area() && r.outcome() == AttackOutcome.DODGED;
            boolean connects = (r.outcome() != AttackOutcome.MISS && r.outcome() != AttackOutcome.DODGED) || areaDodge;
            int hitDamage = atk.damageTotal() == null ? 0 : atk.damageTotal();
            if (areaDodge) hitDamage /= 2; // explosão: Esquiva bem-sucedida = metade
            if (ts.indexOf(t) > 0 && ModuleRules.has(items, "Transpassar")) hitDamage /= 2; // 2º alvo recebe metade do dano
            if (connects && weapon != null && weapon.burst() != null && r.outcome() != AttackOutcome.MISS) {
                int shots = WeaponRules.burstHits(weapon.burst(), atk.total(), rmsg == null ? 0 : rmsg.total(), atk.critical());
                for (int i = 1; i < shots; i++) hitDamage += rollDice(attSheet, calc.dice(), "Rajada: " + att.getName(), userId, role);
                log(e, tp.getId(), "Rajada de " + att.getName() + ": " + shots + " projétil(eis) em " + tp.getName());
            }
            if (connects && calc.dice() != null && tkdKick && effGuard == Guard.INVERTED
                    && t.getReaction() == ReactionType.DODGE && r.outcome() == AttackOutcome.HIT) {
                int hunt = rolls.rollInternal(attSheet, "1d6", "Caçador de Movimento: " + att.getName() + " → " + tp.getName(), userId, role).getTotal();
                hitDamage += hunt;
                log(e, tp.getId(), "Caçador de Movimento (Taekwondo): +" + hunt + " de dano em " + tp.getName());
            }
            String sure = connects && !atk.critical() && calc.dice() != null
                    ? (embalo5 ? "Embalo 5" : guaranteedCrit(a, t, tp, tsh)) : null;
            if (sure != null) {
                // Crítico Garantido é só dano crítico: rola de novo os dados de dano do golpe e soma neste alvo
                int extra = rollDice(attSheet, calc.dice(), "Crítico Garantido (" + sure + "): " + att.getName()
                        + " → " + tp.getName(), userId, role);
                hitDamage += extra;
                log(e, tp.getId(), "Crítico Garantido em " + tp.getName() + " (" + sure + "): +" + extra + " de dano");
            }
            if (connects && attBase != null) {
                MartialType dt = martial.fighter(tp, tsh).type();
                if (attBase.punishes(dt)) { // Punição da base: +1d3 contra o tipo do rótulo
                    int pun = rolls.rollInternal(attSheet, "1d3", "Punição: " + att.getName() + " → " + tp.getName(), userId, role).getTotal();
                    hitDamage += pun;
                    log(e, tp.getId(), "Punição (base de " + att.getName() + "): +" + pun + " de dano");
                }
                if (attBase.heavyAdvance() && tech != null && profiles.hasModule(attSheet, tech, a.getVariation(), "Investida")) {
                    int heavy = EffectRules.takedownDamage(attSheet.getWeightKg()); // dano de Derrubada do lutador
                    hitDamage += heavy;
                    log(e, tp.getId(), "Avanço Pesado (base de " + att.getName() + "): +" + heavy + " de dano");
                }
            }
            if (connects && ModuleRules.has(items, "Montinho")) {
                int pile = Math.max(0, rolls.rollInternal(attSheet, "1d4", "Montinho: " + att.getName() + " → " + tp.getName(),
                        userId, role).getTotal() + SheetRules.weightMod(attSheet.getWeightKg()));
                if (mu.defender().hasArtNamed("Judô")) pile /= 2;
                if (mu.defender().hasArtNamed("Entertainment Wrestling")) {
                    pile = Math.max(0, pile - Math.max(2, RollRules.modOrZero(tsh.getCon())));
                }
                hitDamage += pile;
                pileOn = true;
                log(e, tp.getId(), att.getName() + " cai por cima de " + tp.getName() + " (Montinho): +" + pile + " de dano");
            }
            boolean grab = a.getBasicAttack() == BasicAttack.AGARRAO && a.getSourceType() == RollSourceType.BASIC_ATTACK;
            int dmg = connects && calc.dice() != null && !(grab && !a.isSqueeze())
                    ? AttackRules.damage(hitDamage, r.mitigationPct(), calejo) : 0;
            dmg = afterProtection(e, dmg, a, weapon, tp, tsh);
            if (grab && r.outcome() == AttackOutcome.HIT) grabOn(e, att, attSheet, tp, tsh, a.getGrip());
            if (weapon != null && connects) weaponHit(e, a, weapon, gun, att, attSheet, tp, tsh, atk, vctx);
            if (r.outcome() == AttackOutcome.HIT) basicHit(e, a, att, attSheet, tp, tsh, tpBase, atk, vctx, userId, role);
            int counter = 0;
            if (r.counterHits()) {
                RollMessage c = rolls.rollCalc(rsh, e.getCampaignId(), counterCalc != null ? counterCalc
                        : profiles.basic(rsh, BasicAttack.SOCO, RollProfileService.Opts.NONE), 0,
                        "Contra-ataque: " + rp.getName() + " → " + att.getName(), userId, role);
                int focoCounter = mu.defender().hasArtNamed("Karatê")
                        && martial.consumeCounter(e, rp, "FOCO", null, 1) > 0 ? 2 : 0;
                int raw = (c.damageTotal() == null ? 0 : c.damageTotal()) + focoCounter;
                counter = raw <= 0 ? 0 : AttackRules.damage(raw, 0, attackerCalejo);
                VitalService.Ctx cctx = new VitalService.Ctx(userId, role, "Contra-ataque de " + rp.getName(), c.critical());
                attacksAttacker(e, att, attSheet, counter, cctx);
                if (counterTech != null) {
                    PendingAttack ca = PendingAttack.builder().sourceType(RollSourceType.TECHNIQUE)
                            .sourceId(counterTech.technique().getId()).variation(counterTech.variation())
                            .surface(a.isSurface()).build();
                    applyEffects(e, ca, counterTech.technique(), counterTech.items(), rp, rsh, att, attSheet, c, cctx, userId, role);
                    armedModules(e, ca, counterTech.items(), rp, att, attSheet, c, userId, role);
                }
            }
            if (rr != null && reacted) {
                reactionAftermath(e, rt, rr, r.outcome(), rp, rsh, att, attSheet, rmsg, userId, role);
            }
            ModuleRules.Breath breath = ModuleRules.breath(items);
            if (dmg > 0) {
                if (breath == ModuleRules.Breath.SP_ONLY) {
                    log(e, tp.getId(), "Golpe no Fôlego: " + dmg + " de dano no SP de " + tp.getName());
                    vital.applySp(e, tp, tsh, -dmg, vctx);
                } else {
                    vital.applyHp(e, tp, tsh, -dmg, vctx.withCritical(atk.critical()));
                }
                if (breath == ModuleRules.Breath.BOTH) {
                    int dice = diceTotal(atk);
                    log(e, tp.getId(), "Golpe no Fôlego: −" + dice + " SP em " + tp.getName());
                    vital.applySp(e, tp, tsh, -dice, vctx);
                }
                int persist = ModuleRules.persistentTurns(items);
                if (persist > 0 && dmg / 2 > 0) persistent(e, att, tp, dmg / 2, persist);
                // só golpe contundente tira PQ em Ponto de Nocaute; Impactante soma
                boolean blunt = weapon == null || "BLUNT".equals(weapon.damageType());
                int kp = !blunt ? 0 : VitalRules.kpLoss(a.getRegion(), headBonus(attSheet, a) + basicKp(a, tp, ctx)
                                + (weapon == null ? 0 : weapon.impact()), 0, atk.critical(),
                        VitalRules.activeHysteria(tp.getAdrenaline(), tp.getHysteria()) == Hysteria.WALL)
                        - (rr != null && rr.absorb() ? 1 : 0) - (tpBase != null && tpBase.continuousAbsorb() ? 1 : 0);
                kp -= helmet(tsh, a.getRegion());
                woundsByType(e, weapon, t, r, rsh, tp, tsh, atk, vctx);
                if (kp > 0) vital.applyKp(e, tp, tsh, -kp, vctx);
                if (breath != ModuleRules.Breath.SP_ONLY && (permanentInjury(atk, calc)
                        || mutilates(e, items, atk, tp, tsh, userId, role))) {
                    vital.permanentInjury(e, tp, tsh, dmg, a.getRegion(), vctx);
                }
                martial.fire(e, att, attSheet, "HIT", tp.getId(), moveArt);
                martial.fire(e, tp, tsh, "GOT_HIT", att.getId(), null);
            }
            if (r.outcome() == AttackOutcome.HIT) {
                if (fa.hasArtNamed("Sumo")) {
                    martial.addCounter(e, att, attSheet, "TERRENO_PERDIDO", tp.getId(), atk.critical() ? 2 : 1);
                }
                if (fa.hasArtNamed("Wrestling")
                        && ("Wrestling".equalsIgnoreCase(moveArt) || a.getBasicAttack() == BasicAttack.AGARRAO)) {
                    martial.addCounter(e, att, attSheet, "LUTA_EM_CADEIA", tp.getId(), 1);
                }
                if (fa.hasArtNamed("MMA") && "MMA".equalsIgnoreCase(moveArt)) {
                    att.setFluidSwitchUsed(false);
                }
            }
            if ((r.outcome() == AttackOutcome.MISS || r.outcome() == AttackOutcome.DODGED
                    || r.outcome() == AttackOutcome.COUNTERED)
                    && mu.defender().hasArtNamed("Judô") && (weapon == null || (!weapon.shooter() && !thrownTech))) {
                vital.addEffectCondition(e, att, "DESEQUILIBRADO", ConditionExpiry.END_OF_TARGET_TURN, "Kuzushi", rp.getId());
                log(e, att.getId(), att.getName() + " fica Desequilibrado (Kuzushi de " + rp.getName() + ")");
            }
            Set<String> applied = tech != null && r.outcome() == AttackOutcome.HIT
                    ? applyEffects(e, a, tech, items, att, attSheet, tp, tsh, atk, vctx, userId, role) : Set.of();
            if (tech != null && r.outcome() == AttackOutcome.HIT) armedModules(e, a, items, att, tp, tsh, atk, userId, role);
            if (rr != null && rr.parry() && r.outcome() == AttackOutcome.DEFENDED && weapon == null) {
                WeaponCatalog.Def dw = defenderWeapon(rsh);
                RollRules.Dice dd = dw == null ? null : RollRules.Dice.parse(dw.dice());
                if (dd != null) {
                    int half = rollDice(rsh, dd, "Aparar: " + rp.getName() + " → " + att.getName(), userId, role) / 2;
                    log(e, att.getId(), att.getName() + " bate na arma de " + rp.getName() + " (Aparar): " + half + " de dano");
                    attacksAttacker(e, att, attSheet, half, vctx);
                }
            }
            if (ModuleRules.has(items, "Sequência") && !atk.fumble() && calc.dice() != null) {
                if (second == null) {
                    second = rolls.rollCalc(attSheet, e.getCampaignId(), calc, adv,
                            "Sequência (2º acerto): " + att.getName() + " → " + targetNames, userId, role);
                }
                dmg += sequenceHit(e, a, items, applied, second, t, rmsg, rsh, weapon, breaksGuard, calejo, att, attSheet,
                        tp, tsh, vctx, userId, role);
            }
            switch (r.outcome()) {
                case DEFENDED -> martial.fire(e, rp, rsh, "DEFEND_SUCCESS", att.getId(), null);
                case DODGED -> {
                    martial.fire(e, rp, rsh, "DODGE_SUCCESS", att.getId(), null);
                    if (mu.defender().hasArtNamed("MMA")) rp.setFluidSwitchUsed(false);
                }
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
        if (embalo5) martial.resetCounter(e, att, "EMBALO");
        if (atk.fumble()) basicFumble(e, a, att, attSheet, ctx.participant(ts.get(0).getTargetId()), vctx);
        if (tech != null && gun != null && ModuleRules.has(items, "Disparo Rápido") && weapon.capacity() != null) {
            gun.setLoaded(weapon.capacity()); // a recarga depois da técnica é Ação Livre
        }
        if (pileOn) fallDown(e, att, "Montinho");
        if (!items.isEmpty()) limitationsAfter(e, items, ts, att, attSheet, atk, vctx);
        finish(e, a);
    }

    private void finish(Encounter e, PendingAttack a) {
        a.setStatus(AttackStatus.RESOLVED);
        a.setResolvedAt(Instant.now());
        a.setResolvedTurn(e.getTurnCounter());
        attacks.save(a);
    }

    // ---------- armas (§42) ----------

    /** Item da arma do ataque: o item do Ataque com arma, ou a arma em mãos que a técnica armada usa. */
    private InventoryItem weaponItem(CharacterSheet s, PendingAttack a, Calc calc) {
        if (calc.weapon() == null) return null;
        if (a.getBasicAttack() == BasicAttack.ARMA) {
            return s.getInventory().stream().filter(i -> i.getId().equals(a.getSourceId())).findFirst().orElse(null);
        }
        return RollProfileService.inHand(s).stream().filter(i -> calc.weapon().key().equals(i.getWeaponKey()))
                .findFirst().orElse(null);
    }

    /** Antes de gastar PA: travada, Engatilhamento Lento, munição e alcance máximo (2× o alcance). */
    private void fireCheck(CharacterSheet s, PendingAttack a, Calc calc, InventoryItem gun, boolean extended) {
        WeaponCatalog.Def w = calc.weapon();
        if (w == null || gun == null) return;
        if (gun.isJammed()) throw new InvalidCombatException(gun.getName() + " está travada: destrave (Ação Maior)");
        if (w.slowCock() && !gun.isCocked()) throw new InvalidCombatException("Engatilhe " + gun.getName() + " antes (Ação Menor)");
        boolean supp = a.getSourceType() == RollSourceType.TECHNIQUE
                && profiles.hasModule(s, technique(s, a), a.getVariation(), "Supressão");
        int shots = (w.burst() == null ? 1 : w.burst()) * (supp ? 2 : 1);
        if (w.capacity() != null && !"FAST".equals(w.reload()) && gun.getLoaded() < shots) {
            throw new InvalidCombatException(gun.getName() + " sem munição: recarregue");
        }
        boolean thrownTech = a.getSourceType() == RollSourceType.TECHNIQUE
                && profiles.hasModule(s, technique(s, a), a.getVariation(), "Arremessar");
        Double range = rangeOf(a, w, extended, thrownTech);
        if (range != null && a.getDistanceM() != null && a.getDistanceM() > 2 * range) {
            throw new InvalidCombatException("Fora de alcance: " + gun.getName() + " alcança até " + (2 * range) + " m");
        }
    }

    /** Alcance efetivo: disparo/arremesso da tabela (Disparo Estendido +50%), arremesso improvisado ou módulo Arremessar 6 m. */
    private static Double rangeOf(PendingAttack a, WeaponCatalog.Def w, boolean extended, boolean thrownTech) {
        Double r;
        if ("THROWN".equals(a.getVariation()) || (thrownTech && a.getDistanceM() != null && a.getDistanceM() > WeaponRules.CLOSE_M)) {
            r = w.throwM() != null ? w.throwM() : Double.valueOf(WeaponRules.IMPROVISED_THROW_M);
        } else if (w.rangeM() != null) r = w.rangeM();
        else r = w.throwOnly() ? w.throwM() : null;
        if (r != null && extended) r = r * 1.5;
        return r;
    }

    /** Disparo Estendido da técnica armada: +50% de alcance. */
    private boolean extendedShot(CharacterSheet s, PendingAttack a) {
        return a.getSourceType() == RollSourceType.TECHNIQUE
                && profiles.hasModule(s, technique(s, a), a.getVariation(), "Disparo Estendido");
    }

    /** Disparo: gasta munição e o engatilhamento; arremesso: a arma sai da mão (explosivos são consumidos). */
    private void consumeShot(Encounter e, EncounterParticipant att, CharacterSheet s, PendingAttack a, Calc calc,
                             InventoryItem gun) {
        WeaponCatalog.Def w = calc.weapon();
        if (w == null || gun == null) return;
        boolean supp = a.getSourceType() == RollSourceType.TECHNIQUE
                && profiles.hasModule(s, technique(s, a), a.getVariation(), "Supressão");
        int shots = (w.burst() == null ? 1 : w.burst()) * (supp ? 2 : 1);
        if (w.capacity() != null && !"FAST".equals(w.reload())) gun.setLoaded(Math.max(0, gun.getLoaded() - shots));
        if (w.slowCock()) gun.setCocked(false);
        boolean thrownTech = a.getSourceType() == RollSourceType.TECHNIQUE
                && profiles.hasModule(s, technique(s, a), a.getVariation(), "Arremessar")
                && a.getDistanceM() != null && a.getDistanceM() > WeaponRules.CLOSE_M;
        boolean thrown = w.thrownWeapon() || w.throwOnly() || "THROWN".equals(a.getVariation()) || thrownTech;
        if (!thrown) return;
        if (gun.getQuantity() > 1) {
            gun.setQuantity(gun.getQuantity() - 1);
        } else if (w.area()) {
            s.getInventory().remove(gun); // granada/molotov: consumida
        } else {
            gun.setLocation(ItemLocation.DROPPED); // fica no chão até ser recuperada (Retorno: Ação Menor)
        }
    }

    /** Distância (§42): disparo/arremesso de X a 2X com desvantagem, colado com desvantagem; Grande colada também. */
    private static int rangeAdvantage(PendingAttack a, WeaponCatalog.Def w, boolean extended, boolean thrownTech) {
        if (w == null) return 0;
        int adv = 0;
        Double range = rangeOf(a, w, extended, thrownTech);
        if (range != null && (w.shooter() || w.thrownWeapon() || "THROWN".equals(a.getVariation()) || thrownTech)) {
            Integer r = WeaponRules.rangeAdvantage(range, a.getDistanceM(), w.dispersion());
            adv += r == null ? 0 : r;
        }
        if (w.large() && a.getDistanceM() != null && a.getDistanceM() <= WeaponRules.CLOSE_M) adv--;
        return adv;
    }

    /** Arma travou (1 natural; Instável: ≤ N): o disparo não sai; Instável com 1 natural explode no atirador. */
    private void jammed(Encounter e, PendingAttack a, List<AttackTarget> ts, Ctx ctx, EncounterParticipant att,
                        CharacterSheet attSheet, InventoryItem gun, WeaponCatalog.Def w, RollMessage atk, VitalService.Ctx vctx) {
        if (gun != null) gun.setJammed(true);
        log(e, att.getId(), (gun == null ? w.name() : gun.getName()) + " trava: o disparo não sai (destravar: Ação Maior)");
        if (w.unstable() != null && atk.naturalD20() == 1) {
            int d = atk.damageTotal() == null ? 0 : atk.damageTotal();
            log(e, att.getId(), (gun == null ? w.name() : gun.getName()) + " explode na mão de " + att.getName() + ": " + d + " de dano");
            attacksAttacker(e, att, attSheet, d, vctx.withCritical(false));
        }
        ts.forEach(t -> { t.setOutcome(AttackOutcome.MISS); t.setMitigationPct(0); t.setDamage(0); t.setCounterDamage(0); targets.save(t); });
        finish(e, a);
    }

    // ---------- proteções e equipamento (§43) ----------

    /** Proteção vestida na região atingida (vale a maior), depois da reação e da Defesa; pode zerar o dano. */
    private int afterProtection(Encounter e, int dmg, PendingAttack a, WeaponCatalog.Def w, EncounterParticipant tp,
                                CharacterSheet tsh) {
        if (dmg <= 0) return dmg;
        String cover = EquipmentRules.coverage(a.getRegion());
        String type = w == null ? "BLUNT" : w.damageType();
        boolean firearm = w != null && w.firearm();
        List<EquipmentCatalog.Def> worn = equipment.worn(tsh);
        int red = EquipmentRules.best(worn.stream()
                .map(d -> EquipmentRules.reduction(d.protection(), d.cover(), cover, type, firearm)).toList());
        if (red <= 0) return dmg;
        int left = Math.max(0, dmg - red);
        log(e, tp.getId(), "Proteção de " + tp.getName() + " absorve " + (dmg - left) + " de dano");
        return left;
    }

    /** Capacete: −1 PQ na Têmpora e na Nuca; Capacete de Treino: −1 PQ na Têmpora. */
    private int helmet(CharacterSheet tsh, String region) {
        String r = VitalRules.normalizeRegion(region);
        if (r.contains("tempora") && (equipment.wears(tsh, "HELMET") || equipment.wears(tsh, "TRAINING_HELMET"))) return 1;
        return r.contains("nuca") && equipment.wears(tsh, "HELMET") ? 1 : 0;
    }

    /** Categoria de peso mais pesada vestida (null sem proteção). */
    private String armorWeight(CharacterSheet s) {
        return EquipmentRules.heaviest(equipment.worn(s).stream().map(EquipmentCatalog.Def::category)
                .filter(java.util.Objects::nonNull).toList());
    }

    /** Arma em mãos do defensor (para Defensiva, Flexível e Aparar); null = desarmado. */
    private WeaponCatalog.Def defenderWeapon(CharacterSheet s) {
        if (s == null) return null;
        return RollProfileService.inHand(s).stream().map(i -> weapons.find(i.getWeaponKey()).orElse(null))
                .filter(w -> w != null && !w.augment() && !w.flexible()).findFirst().orElse(null);
    }

    /**
     * Cortante e perfurante abrem 1 Ferida (fogo crítico: 2). Defender desarmado só evita reduzindo 100%; Defender com
     * arma ou objeto evita reduzindo qualquer dano (§42).
     */
    private void woundsByType(Encounter e, WeaponCatalog.Def w, AttackTarget t, AttackRules.Result r, CharacterSheet rsh,
                              EncounterParticipant tp, CharacterSheet tsh, RollMessage atk, VitalService.Ctx vctx) {
        if (w == null || !("SLASH".equals(w.damageType()) || "PIERCE".equals(w.damageType()))) return;
        if (t.getReaction() == ReactionType.DEFEND && r.mitigationPct() > 0 && defenderWeapon(rsh) != null) return;
        int n = w.firearm() && atk.critical() ? 2 : 1;
        log(e, tp.getId(), tp.getName() + " fica com " + n + " Ferida(s) aberta(s) (" + w.name() + ")");
        vital.openWounds(e, tp, tsh, n, vctx);
    }

    /** Propriedades no acerto: Quebradiça, Caco (fere quem empunha), Enredar, Incendiária, Fumaça. */
    private void weaponHit(Encounter e, PendingAttack a, WeaponCatalog.Def w, InventoryItem gun, EncounterParticipant att,
                           CharacterSheet attSheet, EncounterParticipant tp, CharacterSheet tsh, RollMessage atk,
                           VitalService.Ctx vctx) {
        if (w.fragile() && gun != null && !gun.isBroken()) {
            if (w.breaksInto() != null) {
                WeaponCatalog.Def into = weapons.find(w.breaksInto()).orElse(null);
                gun.setWeaponKey(w.breaksInto());
                if (into != null) gun.setName(into.name());
                log(e, att.getId(), w.name() + " quebra e vira " + (into == null ? w.breaksInto() : into.name()));
            } else {
                gun.setBroken(true);
                log(e, att.getId(), w.name() + " quebra no golpe");
            }
        }
        if (w.selfWound()) {
            log(e, att.getId(), att.getName() + " corta a própria mão (" + w.name() + ")");
            vital.openWounds(e, att, attSheet, 1, vctx);
        }
        if (w.entangle()) {
            vital.addEffectCondition(e, tp, "AGARRADO", ConditionExpiry.MANUAL, ENTANGLED, null);
            log(e, tp.getId(), tp.getName() + " fica enredado (" + w.name() + "): deslocamento zero");
        }
        if (w.incendiary()) {
            vital.addEffectCondition(e, tp, VitalService.BURNING, null, w.name(), att.getId());
            conditions.findByParticipantIdIn(List.of(tp.getId())).stream()
                    .filter(c -> VitalService.BURNING.equals(c.getConditionKey())).findFirst()
                    .ifPresent(c -> { c.setIntensity(Math.max(2, c.getIntensity() == null ? 0 : c.getIntensity())); conditions.save(c); });
            log(e, tp.getId(), tp.getName() + " fica Queimando (Intenso); a área queima por 3 rodadas");
        }
        if (w.smoke()) log(e, tp.getId(), "Nuvem de fumaça por 3 rodadas: cobertura Total, ataques dentro dela com desvantagem");
    }

    /** Mutilar (técnica armada, CON): num crítico, ferimento permanente mesmo sem Dano Máximo. */
    private boolean mutilates(Encounter e, List<AssemblyItem> items, RollMessage atk, EncounterParticipant tp,
                              CharacterSheet tsh, UUID userId, ActorRole role) {
        if (!atk.critical() || !ModuleRules.has(items, "Mutilar")) return false;
        EffectRules.Effect fx = new EffectRules.Effect(EffectRules.Kind.CONDITION, null, Attribute.CON, null, null);
        return !resists(e, tp, tsh, fx, "Mutilar", atk.total(), profiles.baseProfile(tsh, tp.getActiveBaseId()), userId, role);
    }

    /** Módulos armados no acerto: Desarmar, Quebrar Arma, Puxar, Supressão. */
    private void armedModules(Encounter e, PendingAttack a, List<AssemblyItem> items, EncounterParticipant att,
                              EncounterParticipant tp, CharacterSheet tsh, RollMessage atk, UUID userId, ActorRole role) {
        BaseRules.Profile db = profiles.baseProfile(tsh, tp.getActiveBaseId());
        InventoryItem held = RollProfileService.inHand(tsh).stream()
                .filter(i -> weapons.find(i.getWeaponKey()).map(w -> !w.augment()).orElse(false)).findFirst().orElse(null);
        if (ModuleRules.has(items, "Desarmar") && held != null && !resists(e, tp, tsh,
                new EffectRules.Effect(EffectRules.Kind.CONDITION, null, Attribute.STR, Attribute.DEX, null), "Desarmar",
                atk.total(), db, userId, role)) {
            held.setLocation(ItemLocation.DROPPED);
            log(e, tp.getId(), held.getName() + " de " + tp.getName() + " cai a 1d3 quadrados (Desarmar)");
        }
        if (ModuleRules.has(items, "Quebrar Arma") && held != null && held.getLocation() == ItemLocation.HAND
                && !resists(e, tp, tsh, new EffectRules.Effect(EffectRules.Kind.CONDITION, null, Attribute.STR, null, null),
                "Quebrar Arma", atk.total(), db, userId, role)) {
            boolean light = weapons.find(held.getWeaponKey()).map(WeaponCatalog.Def::light).orElse(false);
            if (light) held.setBroken(true);
            else held.setDamaged(true);
            log(e, tp.getId(), held.getName() + " de " + tp.getName() + (light ? " se quebra" : " fica danificada (−1 dado)"));
        }
        if (ModuleRules.has(items, "Puxar") && !resists(e, tp, tsh,
                new EffectRules.Effect(EffectRules.Kind.CONDITION, null, Attribute.STR, null, null), "Puxar", atk.total(),
                db, userId, role)) {
            log(e, tp.getId(), tp.getName() + " é puxado 1 quadrado na direção de " + att.getName());
        }
        if (ModuleRules.has(items, "Supressão") && !resists(e, tp, tsh,
                new EffectRules.Effect(EffectRules.Kind.CONDITION, "SUPRIMIDO", Attribute.MEN, null, ConditionExpiry.END_OF_TARGET_TURN),
                "Supressão", atk.total(), db, userId, role)) {
            vital.addEffectCondition(e, tp, "SUPRIMIDO", ConditionExpiry.END_OF_TARGET_TURN, "Supressão", att.getId());
            log(e, tp.getId(), tp.getName() + " fica Suprimido (Supressão): desvantagem no próximo ataque se sair da cobertura");
        }
    }

    private static BasicAttack basicOf(PendingAttack a) {
        return a.getSourceType() == RollSourceType.BASIC_ATTACK ? a.getBasicAttack() : null;
    }

    /**
     * Ataques Básicos que acertaram (§40): Mordida faz quem agarra o mordedor testar Mental ND 12 ou soltar; Empurrão
     * (FOR do alvo contra o acerto) empurra 1,5 m (3 m no crítico) e desfaz o Clinch; Encontrão (melhor de FOR/DES
     * contra acerto + mod. de Peso de quem avança) derruba; Cabeçada na face tira 2 PA do próximo turno, 1× por rodada.
     */
    private void basicHit(Encounter e, PendingAttack a, EncounterParticipant att, CharacterSheet attSheet,
                          EncounterParticipant tp, CharacterSheet tsh, BaseRules.Profile tpBase, RollMessage atk,
                          VitalService.Ctx vctx, UUID userId, ActorRole role) {
        BasicAttack b = basicOf(a);
        if (b == null) return;
        switch (b) {
            case MORDIDA -> {
                if (holding(tp, att) && !vital.willTest(e, tp, tsh, BITE_ND, "Mordida: manter o agarre", vctx)) {
                    releasePair(tp, att);
                    log(e, tp.getId(), tp.getName() + " solta " + att.getName() + " (Mordida)");
                }
            }
            case EMPURRAO -> {
                EffectRules.Effect fx = EffectRules.of("Empurrar").orElseThrow();
                if (!resists(e, tp, tsh, fx, "Empurrar", atk.total(), tpBase, userId, role)) {
                    releasePair(att, tp);
                    log(e, tp.getId(), tp.getName() + " é empurrado " + (atk.critical() ? "3" : "1,5") + " m por " + att.getName());
                }
            }
            case ENCONTRAO -> {
                EffectRules.Effect fx = EffectRules.of("Derrubar").orElseThrow();
                int nd = atk.total() + SheetRules.weightMod(attSheet.getWeightKg());
                if (!resists(e, tp, tsh, fx, "Derrubar", nd, tpBase, userId, role)) {
                    AssemblyItem it = new AssemblyItem();
                    it.setModule(b.label);
                    applyEffect(e, fx, it, false, att, attSheet, tp, tsh, vctx, userId, role);
                }
            }
            case CABECADA -> {
                if (FACE.contains(VitalRules.normalizeRegion(a.getRegion())) && !faceHeadbuttThisRound(e, a, tp)) {
                    tp.setPaLossNext(tp.getPaLossNext() + 2);
                    log(e, tp.getId(), tp.getName() + " perde 2 PA no próximo turno (Cabeçada na face)");
                }
            }
            default -> { }
        }
    }

    /** Falha crítica de Ataque Básico (§40): Cabeçada −1 PQ em quem bate; Chute fica com a perna presa; Encontrão cai. */
    private void basicFumble(Encounter e, PendingAttack a, EncounterParticipant att, CharacterSheet attSheet,
                             EncounterParticipant first, VitalService.Ctx vctx) {
        BasicAttack b = basicOf(a);
        if (b == BasicAttack.CABECADA) {
            log(e, att.getId(), att.getName() + " se fere na Cabeçada: −1 PQ");
            vital.applyKp(e, att, attSheet, -1, vctx);
        } else if (b == BasicAttack.CHUTE) {
            grappleHold(e, att, GrappleRules.GRABBED, first, GrappleRules.Grip.LEG.name());
            log(e, att.getId(), first.getName() + " prende a perna de " + att.getName());
        } else if (b == BasicAttack.ENCONTRAO) {
            vital.addEffectCondition(e, att, KNOCKED_DOWN, null, b.label, null);
            att.setActiveBaseId(null);
            log(e, att.getId(), att.getName() + " cai no Encontrão");
        }
    }

    /** +1 PQ por golpe na cabeça: Chute, Cotovelada, Pisão e Joelhada no Clinch (§40); Ritmo ≥ 3 (Capoeira) soma +1 PQ. */
    private int basicKp(PendingAttack a, EncounterParticipant tp, Ctx ctx) {
        BasicAttack b = basicOf(a);
        CharacterSheet as = ctx.sheetOf.get(a.getAttackerId());
        EncounterParticipant att = ctx.ps.get(a.getAttackerId());
        int ritmoKp = 0;
        if (att != null && as != null && martial.fighter(att, as).hasArtNamed("Capoeira")
                && martial.counterValue(att.getId(), "RITMO", null) >= 3) {
            String art = a.getSourceType() == RollSourceType.TECHNIQUE ? techArtName(technique(as, a)) : null;
            if (b == BasicAttack.CHUTE || "Capoeira".equalsIgnoreCase(art)) ritmoKp = 1;
        }
        int base = 0;
        if (b == BasicAttack.CHUTE || b == BasicAttack.COTOVELADA || b == BasicAttack.PISAO) base = 1;
        else if (b == BasicAttack.SOCO && equipment.wears(as, "MMA_GLOVES")) base = 1;
        else if (b == BasicAttack.JOELHADA && ctx.has(tp.getId(), GrappleRules.CLINCH)) base = 1;
        return base + ritmoKp;
    }

    private static String techArtName(CharacterTechnique t) {
        return t == null || t.getCharacterArt() == null ? null : t.getCharacterArt().getArtName();
    }

    private boolean faceHeadbuttThisRound(Encounter e, PendingAttack a, EncounterParticipant tp) {
        List<UUID> ids = attacks.findByEncounterIdAndAttackerIdAndStatus(e.getId(), a.getAttackerId(), AttackStatus.RESOLVED)
                .stream().filter(x -> x.getRound() == a.getRound() && basicOf(x) == BasicAttack.CABECADA
                        && FACE.contains(VitalRules.normalizeRegion(x.getRegion())))
                .map(PendingAttack::getId).toList();
        return !ids.isEmpty() && targets.findByAttackIdIn(ids).stream()
                .anyMatch(t -> t.getTargetId().equals(tp.getId()) && t.getOutcome() == AttackOutcome.HIT);
    }

    /** `holder` mantém `victim` preso (alguma condição de agarre em `victim` com origem em `holder`). */
    private boolean holding(EncounterParticipant holder, EncounterParticipant victim) {
        return conditions.findByParticipantIdIn(List.of(victim.getId())).stream()
                .anyMatch(c -> holder.getId().equals(c.getSourceParticipantId()) && GrappleRules.HOLDS.contains(c.getConditionKey()));
    }

    /** Desfaz o agarre entre os dois, nos dois sentidos (inclui o Clinch mútuo). */
    private void releasePair(EncounterParticipant x, EncounterParticipant y) {
        conditions.findByParticipantIdIn(List.of(x.getId(), y.getId())).stream()
                .filter(c -> GrappleRules.HOLDS.contains(c.getConditionKey()) && c.getSourceParticipantId() != null
                        && (c.getParticipantId().equals(x.getId()) ? y : x).getId().equals(c.getSourceParticipantId()))
                .toList().forEach(conditions::delete);
    }

    /**
     * Efeitos de módulo no acerto (Criação Marcial, plano §30): "no dado" só com 17+ natural; o alvo resiste com o
     * atributo do efeito (o melhor de dois em Derrubar) contra o resultado do teste — ou ND 10 + mod. de FOR com
     * **Pressão Fixa**; crítico só dispensa o teste com **Crítico Implacável**. Enraizado e Resiliência da base do
     * alvo somam na resistência. Efeito sem regra automática vira aviso ao Mestre.
     */
    private Set<String> applyEffects(Encounter e, PendingAttack a, CharacterTechnique tech, List<AssemblyItem> all,
                                     EncounterParticipant att, CharacterSheet attSheet, EncounterParticipant tp,
                                     CharacterSheet tsh, RollMessage atk, VitalService.Ctx vctx, UUID userId,
                                     ActorRole role) {
        List<AssemblyItem> items = all.stream().filter(i -> i.getKind() == AssemblyItemKind.EFFECT).toList();
        Set<String> applied = new HashSet<>();
        if (items.isEmpty()) return applied;
        boolean implacable = atk.critical() && profiles.hasModule(attSheet, tech, a.getVariation(), "Crítico Implacável");
        boolean fixed = profiles.hasModule(attSheet, tech, a.getVariation(), "Pressão Fixa");
        int nd = EffectRules.nd(atk.total(), fixed, RollRules.modOrZero(attSheet.getStr()));
        boolean control = profiles.core(attSheet, tech, a.getVariation()) == TechniqueCore.CONTROL;
        BaseRules.Profile db = profiles.baseProfile(tsh, tp.getActiveBaseId());
        String moveArt = techArtName(tech);
        boolean finisher = atk.breakdown() != null
                && atk.breakdown().stream().anyMatch(p -> p.source() != null && p.source().startsWith("Finisher"));
        for (AssemblyItem it : items) {
            if (!EffectRules.triggers(it.isOnDie(), atk.naturalD20(), atk.critical())) continue;
            String label = it.getModule();
            EffectRules.Effect fx = EffectRules.of(label).orElse(null);
            if (fx == null) {
                log(e, tp.getId(), "Efeito " + label + " de " + att.getName() + " em " + tp.getName() + ": aplicar manualmente");
                continue;
            }
            if (fx.kind() == EffectRules.Kind.DEEPEN_WOUND && tsh.getOpenWounds() == 0) continue; // exige Ferida aberta
            if (fx.kind() == EffectRules.Kind.CRUSH && !a.isSurface()) {
                log(e, tp.getId(), "Esmagar de " + att.getName() + ": sem superfície sólida junto de " + tp.getName());
                continue;
            }
            if (fx.resist() != null && !implacable && resists(e, tp, tsh, fx, label, nd, db, moveArt, finisher, userId, role)) continue;
            applyEffect(e, fx, it, control, att, attSheet, tp, tsh, vctx, userId, role);
            applied.add(label);
            if (fx.kind() == EffectRules.Kind.TAKEDOWN && ModuleRules.has(all, "Arremesso")) {
                throwDamage(e, att, attSheet, tp, tsh, ModuleRules.has(all, "Cravar"), vctx, userId, role);
            }
        }
        return applied;
    }

    /** Arremesso (§41): a queda dói conforme o chão do encontro; Cravar é sempre chão duro (+1 PQ). */
    private void throwDamage(Encounter e, EncounterParticipant att, CharacterSheet attSheet, EncounterParticipant tp,
                             CharacterSheet tsh, boolean spike, VitalService.Ctx vctx, UUID userId, ActorRole role) {
        String dice = ModuleRules.throwDice(e.getFloor(), spike);
        int d = dice == null ? 0 : rolls.rollInternal(attSheet, dice, (spike ? "Cravar" : "Arremesso") + ": " + att.getName()
                + " → " + tp.getName(), userId, role).getTotal();
        int kp = ModuleRules.throwKp(e.getFloor(), spike);
        if (martial.fighter(tp, tsh).hasArtNamed("Entertainment Wrestling")) {
            d = Math.max(0, d - Math.max(2, RollRules.modOrZero(tsh.getCon())));
            kp = 0; // Bump Master: não perde PQ por Arremesso nem Cravar
        }
        log(e, tp.getId(), tp.getName() + " cai " + (spike ? "cravado" : "arremessado") + ": " + d + " de dano"
                + (kp > 0 ? " e −" + kp + " PQ" : "") + (dice == null ? " (chão macio)" : ""));
        if (d > 0) vital.applyHp(e, tp, tsh, -d, vctx.withCritical(false));
        if (kp > 0) vital.applyKp(e, tp, tsh, -kp, vctx);
    }

    /**
     * Sequência (§41): o 2º acerto compara com o resultado da mesma reação (o alvo não rola de novo); metade do dano,
     * sem Efeitos nem contra-ataque. Golpe no Fôlego, Dano Persistente, Montinho, proteção e Ferida por tipo de dano
     * valem também neste acerto. Empurrão Contínuo: se o Empurrar do 1º valeu, a distância aumenta pela metade.
     * Devolve o dano causado.
     */
    private int sequenceHit(Encounter e, PendingAttack a, List<AssemblyItem> items, Set<String> applied, RollMessage second,
                            AttackTarget t, RollMessage rmsg, CharacterSheet rsh, WeaponCatalog.Def weapon,
                            boolean breaksGuard, int calejo, EncounterParticipant att, CharacterSheet attSheet,
                            EncounterParticipant tp, CharacterSheet tsh, VitalService.Ctx vctx, UUID userId, ActorRole role) {
        AttackRules.Result r2 = AttackRules.resolve(t.getReaction(), second.total(), second.critical(), second.fumble(),
                rmsg == null ? 0 : rmsg.total(), rmsg != null && rmsg.critical(), breaksGuard);
        boolean hit = r2.outcome() != AttackOutcome.MISS && r2.outcome() != AttackOutcome.DODGED
                && r2.outcome() != AttackOutcome.COUNTERED;
        int raw = second.damageTotal() == null ? 0 : second.damageTotal() / 2;
        if (hit && ModuleRules.has(items, "Montinho")) {
            int pile = Math.max(0, rolls.rollInternal(attSheet, "1d4", "Montinho (Sequência): " + att.getName() + " → " + tp.getName(),
                    userId, role).getTotal() + SheetRules.weightMod(attSheet.getWeightKg())) / 2;
            raw += pile;
        }
        int d = hit ? AttackRules.damage(raw, r2.mitigationPct(), calejo) : 0;
        d = afterProtection(e, d, a, weapon, tp, tsh);
        log(e, tp.getId(), "Sequência de " + att.getName() + ": 2º acerto " + second.total()
                + (hit ? " — " + d + " de dano em " + tp.getName() : " — não passou"));
        if (d > 0) {
            ModuleRules.Breath breath = ModuleRules.breath(items);
            if (breath == ModuleRules.Breath.SP_ONLY) {
                log(e, tp.getId(), "Golpe no Fôlego (Sequência): " + d + " de dano no SP de " + tp.getName());
                vital.applySp(e, tp, tsh, -d, vctx);
            } else {
                vital.applyHp(e, tp, tsh, -d, vctx.withCritical(second.critical()));
            }
            if (breath == ModuleRules.Breath.BOTH) {
                int dice = diceTotal(second) / 2;
                if (dice > 0) {
                    log(e, tp.getId(), "Golpe no Fôlego (Sequência): −" + dice + " SP em " + tp.getName());
                    vital.applySp(e, tp, tsh, -dice, vctx);
                }
            }
            int persist = ModuleRules.persistentTurns(items);
            if (persist > 0 && d / 2 > 0) persistent(e, att, tp, d / 2, persist);
            woundsByType(e, weapon, t, r2, rsh, tp, tsh, second, vctx);
        }
        if (hit && applied.contains("Empurrar") && ModuleRules.has(items, "Empurrão Contínuo")) {
            log(e, tp.getId(), tp.getName() + " é empurrado mais metade da distância (Empurrão Contínuo)");
        }
        return d;
    }

    /** Risco, Desguarnecida e Exposta depois do golpe (§41). Errar = nenhum alvo foi acertado. */
    private void limitationsAfter(Encounter e, List<AssemblyItem> items, List<AttackTarget> ts, EncounterParticipant att,
                                  CharacterSheet attSheet, RollMessage atk, VitalService.Ctx vctx) {
        boolean missed = ts.stream().allMatch(t -> t.getOutcome() == AttackOutcome.MISS
                || t.getOutcome() == AttackOutcome.DODGED || t.getOutcome() == AttackOutcome.COUNTERED);
        if (missed) {
            switch (ModuleRules.risk(items)) {
                case FALL -> fallDown(e, att, "Risco");
                case SELF_DAMAGE -> {
                    int d = atk.damageTotal() == null ? 0 : atk.damageTotal();
                    log(e, att.getId(), att.getName() + " sofre o próprio golpe (Risco): " + d + " de dano");
                    if (d > 0) vital.applyHp(e, att, attSheet, -d, vctx.withCritical(false));
                }
                default -> { }
            }
        }
        if (ModuleRules.has(items, "Exposta") || (missed && ModuleRules.has(items, "Desguarnecida"))) {
            vital.addEffectCondition(e, att, "VULNERAVEL", ConditionExpiry.START_OF_TARGET_TURN,
                    ModuleRules.has(items, "Exposta") ? "Exposta" : "Desguarnecida", null);
            log(e, att.getId(), att.getName() + " fica Vulnerável até o início do próximo turno");
        }
    }

    private void fallDown(Encounter e, EncounterParticipant p, String why) {
        vital.addEffectCondition(e, p, KNOCKED_DOWN, null, why, null);
        p.setActiveBaseId(null);
        log(e, p.getId(), p.getName() + " fica Derrubado (" + why + ")");
    }

    /** Dano Persistente (§41): metade do dano de novo no início dos próximos turnos do alvo. */
    private void persistent(Encounter e, EncounterParticipant att, EncounterParticipant tp, int amount, int turns) {
        CombatCondition c = conditions.findByParticipantIdIn(List.of(tp.getId())).stream()
                .filter(x -> VitalService.PERSISTENT.equals(x.getConditionKey())).findFirst()
                .orElseGet(() -> CombatCondition.builder().participantId(tp.getId()).conditionKey(VitalService.PERSISTENT).build());
        c.setIntensity(Math.max(amount, c.getIntensity() == null ? 0 : c.getIntensity()));
        c.setExpiresAt(ConditionExpiry.COUNTER);
        c.setTurnsRemaining(Math.max(turns, c.getTurnsRemaining() == null ? 0 : c.getTurnsRemaining()));
        c.setSourceParticipantId(att.getId());
        c.setAppliedTurn(e.getTurnCounter());
        conditions.save(c);
        log(e, tp.getId(), tp.getName() + " sofre Dano Persistente: " + amount + " por " + turns + " turno(s)");
    }

    /** Soma dos dados de dano rolados no ataque (sem parcelas fixas; Fúria fora). */
    private static int diceTotal(RollMessage atk) {
        return atk.breakdown() == null ? 0 : atk.breakdown().stream()
                .filter(p -> "DAMAGE".equals(p.part()) && p.dice() != null && !p.source().startsWith("Fúria"))
                .mapToInt(RollPart::value).sum();
    }

    /** Ímpeto (+2 por 1,5 m) e Previsível (−2 por uso anterior contra o mesmo alvo), §41. */
    private Calc withModules(Calc c, PendingAttack a, List<AssemblyItem> items, EncounterParticipant first) {
        if (items.isEmpty()) return c;
        List<RollPart> hit = new ArrayList<>(c.hit());
        List<RollPart> damage = new ArrayList<>(c.damage());
        int imp = ModuleRules.has(items, "Ímpeto") ? ModuleRules.impetus(a.getRunMeters()) : 0;
        if (imp > 0 && c.dice() != null) damage.add(new RollPart("DAMAGE", "Ímpeto (" + a.getRunMeters() + " m)", imp, null, false));
        if (ModuleRules.has(items, "Previsível")) {
            List<UUID> prior = attacks.findByEncounterIdAndAttackerIdAndStatus(a.getEncounterId(), a.getAttackerId(),
                    AttackStatus.RESOLVED).stream().filter(x -> !x.getId().equals(a.getId())
                    && a.getSourceId().equals(x.getSourceId())).map(PendingAttack::getId).toList();
            long uses = prior.isEmpty() ? 0 : targets.findByAttackIdIn(prior).stream()
                    .filter(t -> t.getTargetId().equals(first.getId())).count();
            int pen = ModuleRules.predictable(items, (int) uses);
            if (pen != 0) hit.add(new RollPart("HIT", "Previsível", pen, null, false));
        }
        return c.with(c.spCost(), c.critMargin(), c.advantage(), hit, damage);
    }

    /** Aplica um Efeito já não resistido: `src` é quem causou (atacante, ou quem reagiu com técnica), `tp` o alvo. */
    private void applyEffect(Encounter e, EffectRules.Effect fx, AssemblyItem it, boolean control, EncounterParticipant src,
                             CharacterSheet srcSheet, EncounterParticipant tp, CharacterSheet tsh, VitalService.Ctx vctx,
                             UUID userId, ActorRole role) {
        String label = it.getModule();
        int qty = Math.max(1, it.getQty());
        switch (fx.kind()) {
            case CONDITION, TAKEDOWN -> {
                String key = EffectRules.escalate(EffectRules.condition(fx, control), vital.hasCondition(tp, "MEIO_CEGO"));
                vital.addEffectCondition(e, tp, key, fx.expiry(), label, src.getId());
                if ((key.equals(KNOCKED_DOWN) || key.equals("QUEBRA_DE_BASE")) && tp.getActiveBaseId() != null) {
                    tp.setActiveBaseId(null);
                    log(e, tp.getId(), tp.getName() + " perdeu a base ativa");
                }
                if (key.equals(KNOCKED_DOWN)) {
                    conditions.findByParticipantIdIn(List.of(tp.getId())).stream()
                            .filter(c -> "DESEQUILIBRADO".equals(c.getConditionKey()))
                            .toList().forEach(conditions::delete);
                    if (martial.fighter(src, srcSheet).hasArtNamed("Entertainment Wrestling")) {
                        martial.addCounter(e, src, srcSheet, "POP", null, 1);
                    }
                }
                if (key.equals(KNOCKED_DOWN) || key.equals("AGARRADO") || key.equals("IMOBILIZADO")) {
                    martial.fire(e, tp, tsh, key.equals(KNOCKED_DOWN) ? "KNOCKED_DOWN" : "GRAPPLED", null, null);
                }
                if (fx.kind() == EffectRules.Kind.TAKEDOWN) {
                    int dmg = EffectRules.takedownDamage(tsh.getWeightKg());
                    MartialService.Fighter fd = martial.fighter(tp, tsh);
                    if (fd.hasArtNamed("Judô")) dmg /= 2; // Ukemi
                    if (fd.hasArtNamed("Entertainment Wrestling")) {
                        dmg = Math.max(0, dmg - Math.max(2, RollRules.modOrZero(tsh.getCon()))); // Bump Master
                    }
                    log(e, tp.getId(), tp.getName() + " sofre " + dmg + " de dano de Derrubada");
                    if (dmg > 0) vital.applyHp(e, tp, tsh, -dmg, vctx.withCritical(false));
                }
            }
            case PUSH -> {
                int m = Math.max(0, rolls.rollInternal(srcSheet, "1d3", "Empurrar: " + src.getName() + " → " + tp.getName(),
                        userId, role).getTotal() + RollRules.modOrZero(srcSheet.getStr()));
                if (martial.fighter(src, srcSheet).hasArtNamed("Sumo")) {
                    martial.addCounter(e, src, srcSheet, "TERRENO_PERDIDO", tp.getId(), 1);
                }
                log(e, tp.getId(), tp.getName() + " é empurrado " + m + " m (colidindo, +1d4 de dano: Mestre)");
            }
            case STEAL_PA -> {
                int pa = EffectRules.stolenPa(qty);
                tp.setPaLossNext(tp.getPaLossNext() + pa);
                log(e, tp.getId(), tp.getName() + " perde " + pa + " PA no próximo turno (" + label + ")");
            }
            case WOUND, DEEPEN_WOUND -> vital.openWounds(e, tp, tsh, 1, vctx);
            case CRUSH -> { // prensado contra a superfície: Impacto 1d4+3 e +1 PQ
                int d = rolls.rollInternal(srcSheet, ModuleRules.CRUSH_DAMAGE, "Esmagar: " + src.getName() + " → "
                        + tp.getName(), userId, role).getTotal() + ModuleRules.CRUSH_FLAT;
                log(e, tp.getId(), tp.getName() + " é prensado (Esmagar): " + d + " de dano e −1 PQ");
                vital.applyHp(e, tp, tsh, -d, vctx.withCritical(false));
                vital.applyKp(e, tp, tsh, -1, vctx);
            }
        }
    }

    /**
     * Agarrão que acertou (§39): ponto tronco/nuca entra em Clinch mútuo (nota INICIOU em quem sofreu, para saber quem
     * iniciou); braço, perna e pescoço deixam o alvo Agarrado com o ponto na nota.
     */
    private void grabOn(Encounter e, EncounterParticipant att, CharacterSheet attSheet, EncounterParticipant tp,
                        CharacterSheet tsh, GrappleRules.Grip grip) {
        GrappleRules.Grip g = grip == null ? GrappleRules.Grip.ARM : grip;
        if (GrappleRules.clinch(g)) {
            grappleHold(e, tp, GrappleRules.CLINCH, att, GrappleRules.CLINCH_INIT);
            grappleHold(e, att, GrappleRules.CLINCH, tp, null);
        } else {
            grappleHold(e, tp, GrappleRules.GRABBED, att, g.name());
        }
        martial.fire(e, tp, tsh, "GRAPPLED", null, null);
        log(e, tp.getId(), att.getName() + " agarra " + tp.getName() + " (" + g.label + ")"
                + (GrappleRules.clinch(g) ? ": Clinch" : ""));
    }

    private void grappleHold(Encounter e, EncounterParticipant t, String key, EncounterParticipant source, String note) {
        CombatCondition c = conditions.findByParticipantIdIn(List.of(t.getId())).stream()
                .filter(x -> key.equals(x.getConditionKey()) && source.getId().equals(x.getSourceParticipantId()))
                .findFirst().orElseGet(() -> CombatCondition.builder().participantId(t.getId()).conditionKey(key).build());
        c.setSourceParticipantId(source.getId());
        c.setNote(note);
        c.setExpiresAt(ConditionExpiry.MANUAL);
        c.setAppliedTurn(e.getTurnCounter());
        conditions.save(c);
    }

    private boolean resists(Encounter e, EncounterParticipant tp, CharacterSheet tsh, EffectRules.Effect fx, String label,
                            int nd, BaseRules.Profile db, UUID userId, ActorRole role) {
        return resists(e, tp, tsh, fx, label, nd, db, null, false, userId, role);
    }

    /** Teste de resistência a um Efeito; true = resistiu (já registra na linha do tempo). */
    private boolean resists(Encounter e, EncounterParticipant tp, CharacterSheet tsh, EffectRules.Effect fx, String label,
                            int nd, BaseRules.Profile db, String moveArt, boolean finisher, UUID userId, ActorRole role) {
        Attribute attr = fx.alt() != null && RollRules.modOrZero(tsh.getAttribute(fx.alt()))
                > RollRules.modOrZero(tsh.getAttribute(fx.resist())) ? fx.alt() : fx.resist();
        if (vital.hasCondition(tp, "PARALISADO") && (attr == Attribute.STR || attr == Attribute.DEX)) return false;
        Calc rc = profiles.attribute(tsh, attr);
        List<RollPart> hit = new ArrayList<>(rc.hit());
        if (db != null && EffectRules.ROOTED.contains(label) && db.rooted() != 0) {
            hit.add(new RollPart("HIT", "Enraizado (base: " + db.name() + ")", db.rooted(), null, false));
        }
        if (db != null && ("Desarmar".equals(label) || "Quebrar Arma".equals(label)) && db.firmGrip() != 0) {
            hit.add(new RollPart("HIT", "Empunhadura Firme (base: " + db.name() + ")", db.firmGrip(), null, false));
        }
        if (db != null && db.resilience() != 0) {
            hit.add(new RollPart("HIT", "Resiliência (base: " + db.name() + ")", db.resilience(), null, false));
        }
        if (EffectRules.ROOTED.contains(label) && martial.fighter(tp, tsh).hasArtNamed("Sumo")) {
            int wm = Math.max(0, SheetRules.weightMod(tsh.getWeightKg()));
            if (wm > 0) hit.add(new RollPart("HIT", "Preso ao Solo +" + wm, wm, null, false));
        }
        if ("Wrestling".equalsIgnoreCase(moveArt) && tsh.getCurrentSp() < tsh.getSpMax() / 2) {
            hit.add(new RollPart("HIT", "Fôlego de Altitude −2", -2, null, false));
        }
        int extraAdv = 0;
        if ("Derrubar".equals(label) && vital.hasCondition(tp, "DESEQUILIBRADO")) {
            extraAdv--;
            hit.add(new RollPart("HIT", "Kuzushi (Ceder: desvantagem)", 0, null, false));
        }
        if (finisher) {
            extraAdv--;
            hit.add(new RollPart("HIT", "Finisher (desvantagem)", 0, null, false));
        }
        rc = new Calc(rc.type(), rc.sourceId(), rc.name(), rc.variation(), rc.basic(), rc.spCost(), rc.critMargin(),
                rc.advantage(), hit, rc.dice(), rc.damage());
        RollMessage res = rolls.rollCalc(tsh, e.getCampaignId(), rc, vital.conditionAdvantage(tp, tsh, attr) + extraAdv,
                "Resistir a " + label + ": " + tp.getName() + " vs " + nd, userId, role);
        boolean ok = EffectRules.resisted(res.total(), nd);
        if (ok) log(e, tp.getId(), tp.getName() + " resistiu a " + label + " (" + res.total() + " vs " + nd + ")");
        return ok;
    }

    /**
     * Situação do golpe (Ataques Básicos e condições): Soco em Ação Menor sem a Força; Joelhada −2 exceto contra alvo
     * Agarrado ou em Clinch; Enfraquecido −2 de dano; alvo Marcado dá +1 aos aliados de quem marcou; Abertura dá
     * vantagem; Carga da Preparação soma dano (as duas são consumidas).
     */
    private Calc withSituation(Calc c, PendingAttack a, EncounterParticipant att, EncounterParticipant first, Ctx ctx) {
        List<RollPart> hit = new ArrayList<>(c.hit());
        List<RollPart> damage = new ArrayList<>(c.damage());
        BasicAttack b = a.getSourceType() == RollSourceType.BASIC_ATTACK ? a.getBasicAttack() : null;
        if (b == BasicAttack.SOCO && a.getActionKind() == AttackActionKind.MINOR_OFFENSIVE) {
            damage.removeIf(p -> "FOR".equals(p.source()));
        }
        CharacterSheet as = ctx.sheetOf.get(att.getId());
        CharacterSheet fs = ctx.sheetOf.get(first.getId());
        CharacterTechnique tech = a.getSourceType() == RollSourceType.TECHNIQUE && as != null ? technique(as, a) : null;
        String artName = techArtName(tech);
        MartialService.Fighter fa = as == null ? null : martial.fighter(att, as);
        if ("Wrestling".equalsIgnoreCase(artName) && fs != null && fs.getCurrentSp() < fs.getSpMax() / 2) {
            hit.add(new RollPart("HIT", "Fôlego de Altitude +2", 2, null, false));
        }
        if (fa != null && fa.hasArtNamed("Wrestling") && ("Wrestling".equalsIgnoreCase(artName) || b == BasicAttack.AGARRAO)) {
            int chain = martial.counterValue(att.getId(), "LUTA_EM_CADEIA", first.getId());
            if (chain > 0) hit.add(new RollPart("HIT", "Luta em Cadeia +" + chain, chain, null, false));
        }
        if ("Judô".equalsIgnoreCase(artName) && tech != null && profiles.hasModule(as, tech, a.getVariation(), "Derrubar")
                && ctx.has(first.getId(), "DESEQUILIBRADO") && fs != null && c.dice() != null) {
            int fAlheia = Math.max(0, RollRules.modOrZero(fs.getStr()));
            if (fAlheia > 0) damage.add(new RollPart("DAMAGE", "Kuzushi (Força Alheia) +" + fAlheia, fAlheia, null, false));
        }
        if (b == BasicAttack.SOCO && equipment.wears(as, "MMA_GLOVES")) damage.add(new RollPart("DAMAGE", "Luvas de MMA", -3, null, false));
        int glovesAdv = b == BasicAttack.AGARRAO && equipment.wears(as, "BOXING_GLOVES") ? -1 : 0;
        if (b == BasicAttack.JOELHADA && ctx.hasAny(first.getId(), Set.of("AGARRADO", "CLINCH"))) {
            hit.removeIf(p -> BasicAttack.JOELHADA.label.equals(p.source()));
        }
        if (ctx.has(att.getId(), "ENFRAQUECIDO")) damage.add(new RollPart("DAMAGE", "Enfraquecido", -2, null, false));
        boolean marked = conditions.findByParticipantIdIn(List.of(first.getId())).stream()
                .filter(k -> "MARCADO".equals(k.getConditionKey()) && k.getSourceParticipantId() != null
                        && !k.getSourceParticipantId().equals(att.getId()))
                .map(k -> ctx.ps.get(k.getSourceParticipantId()))
                .anyMatch(src -> src != null && src.getTeam() == att.getTeam());
        if (marked) hit.add(new RollPart("HIT", "Alvo Marcado", 1, null, false));
        int adv = c.advantage() + glovesAdv;
        if (glovesAdv != 0) hit.add(new RollPart("HIT", "Luvas de Boxe: Agarrão com desvantagem", 0, null, false));
        // Abertura: o próximo ataque contra quem foi defendido/esquivado tem vantagem (consumida aqui)
        CombatCondition opening = conditions.findByParticipantIdIn(List.of(att.getId())).stream()
                .filter(k -> "ABERTURA".equals(k.getConditionKey()) && first.getId().equals(k.getSourceParticipantId()))
                .findFirst().orElse(null);
        if (opening != null) {
            adv++;
            hit.add(new RollPart("HIT", "Abertura (vantagem)", 0, null, false));
            conditions.delete(opening);
        }
        // Suprimido: o próximo ataque tem desvantagem (consumida aqui)
        CombatCondition supp = conditions.findByParticipantIdIn(List.of(att.getId())).stream()
                .filter(k -> "SUPRIMIDO".equals(k.getConditionKey())).findFirst().orElse(null);
        if (supp != null) {
            adv--;
            hit.add(new RollPart("HIT", "Suprimido (desvantagem)", 0, null, false));
            conditions.delete(supp);
        }
        // Carga da Preparação: +dano no próximo golpe (consumida)
        Prepared pr = c.dice() == null ? null : prepared(att, ctx.sheetOf.get(att.getId()));
        if (pr != null && pr.prep().charge() > 0 && pr.flags().contains("C")) {
            damage.add(new RollPart("DAMAGE", "Carga (" + pr.tech().name() + ")", pr.prep().charge(), null, false));
            consume(pr, "C");
        }
        return c.with(c.spCost(), c.critMargin(), adv, hit, damage);
    }

    /** Parcelas marciais do atacante: especialização (básico), guarda, interação e contadores/passivas das Artes Clássicas. */
    private Calc withMartial(Encounter e, Calc c, PendingAttack a, EncounterParticipant att, CharacterSheet s, boolean downed,
                             MartialService.Fighter fa, CharacterTechnique tech, Inter inter) {
        List<RollPart> hit = new ArrayList<>(c.hit());
        List<RollPart> damage = new ArrayList<>(c.damage());
        int adv = c.advantage();
        RollRules.Dice dice = c.dice();
        String artName = techArtName(tech);
        boolean basic = a.getSourceType() == RollSourceType.BASIC_ATTACK && a.getBasicAttack() != null
                && a.getBasicAttack().unarmed(); // a especialização é dos golpes do corpo
        if (basic && !downed && (!fa.hasArt() || !fa.attack().contains(MartialRules.family(a.getBasicAttack())))) {
            halve(hit, "DES", "Fora da especialização: DES/2");
        }
        if (!downed && fa.hasArtNamed("Jiu-Jitsu Brasileiro")
                && (a.getBasicAttack() == BasicAttack.SOCO || a.getBasicAttack() == BasicAttack.CHUTE)) {
            hit.add(new RollPart("HIT", "Arte Suave: trocação em pé −3", -3, null, false));
        }
        if (fa.hasArtNamed("Karatê") && martial.consumeCounter(e, att, "FOCO", null, 1) > 0) {
            hit.add(new RollPart("HIT", "Foco (Zanshin) +2", 2, null, false));
            if (dice != null) damage.add(new RollPart("DAMAGE", "Foco (Zanshin) +2", 2, null, false));
        }
        boolean kickOrCapoeira = a.getBasicAttack() == BasicAttack.CHUTE || "Capoeira".equalsIgnoreCase(artName);
        int ritmo = fa.hasArtNamed("Capoeira") ? martial.counterValue(att.getId(), "RITMO", null) : 0;
        if (ritmo >= 1) {
            int rh = ritmo >= 2 && "Capoeira".equalsIgnoreCase(artName) ? 2 : 1;
            hit.add(new RollPart("HIT", "Ritmo +" + rh, rh, null, false));
        }
        if (ritmo >= 3 && kickOrCapoeira && dice != null) {
            damage.add(new RollPart("DAMAGE", "Ritmo (3 PR) +3", 3, null, false));
        }
        if (ritmo >= 4) {
            adv++;
            hit.add(new RollPart("HIT", "Ritmo (4 PR: vantagem)", 0, null, false));
        }
        if (ritmo >= 5 && kickOrCapoeira && dice != null) {
            dice = WeaponRules.heavier(dice, 1);
        }
        int embalo = fa.hasArtNamed("Boxe") && (a.getBasicAttack() == BasicAttack.SOCO || "Boxe".equalsIgnoreCase(artName))
                ? martial.counterValue(att.getId(), "EMBALO", null) : 0;
        if (embalo > 0) {
            hit.add(new RollPart("HIT", "Embalo +" + embalo, embalo, null, false));
            if (dice != null) damage.add(new RollPart("DAMAGE", "Embalo +" + embalo, embalo, null, false));
        }
        if ("Taekwondo".equalsIgnoreCase(artName) && dice != null) {
            int fc = martial.consumeCounter(e, att, "FORCA_CENTRIFUGA", null, 10);
            if (fc > 0) damage.add(new RollPart("DAMAGE", "Força Centrífuga +" + fc, fc, null, false));
        }
        if ("Entertainment Wrestling".equalsIgnoreCase(artName) && martial.counterValue(att.getId(), "POP", null) >= 3) {
            martial.consumeCounter(e, att, "POP", null, 3);
            adv++;
            hit.add(new RollPart("HIT", "Finisher (3 Pop: vantagem)", 0, null, false));
        }
        StrikeSide side = a.getStrikeSide();
        if (c.weapon() != null) {
            BaseRules.Profile ownBase = profiles.baseProfile(s, att.getActiveBaseId());
            if (ownBase != null && ownBase.steadyAim() > 0 && c.weapon().shooter() && !"THROWN".equals(a.getVariation())
                    && att.getMovesThisTurn() == 0) {
                hit.add(new RollPart("HIT", "Mira Estável (base: " + ownBase.name() + ")", ownBase.steadyAim(), null, false));
            }
            // Arma de uma mão: sem bônus de guarda; lado não dominante sofre −1 no acerto (Duas Mãos: nenhum modificador)
            InventoryItem wi = weaponItem(s, a, c);
            boolean twoHands = c.weapon().forcesTwoHands() || (wi != null && wi.isTwoHanded() && c.weapon().variable());
            if (!twoHands && s.getDominance() != Dominance.AMBIDEXTROUS) {
                List<InventoryItem> handWeapons = RollProfileService.inHand(s).stream()
                        .filter(i -> weapons.find(i.getWeaponKey()).map(w -> !w.augment()).orElse(false)).toList();
                boolean secondHand = handWeapons.size() > 1 && wi != null && handWeapons.get(1).getId().equals(wi.getId());
                if (side == StrikeSide.BACK || secondHand) {
                    hit.add(new RollPart("HIT", "Dominância: lado não dominante −1", -1, null, false));
                }
            }
        } else if (side != null && (tech != null || (basic && MartialRules.oneSided(a.getBasicAttack())))) {
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
        return c.with(c.spCost(), c.critMargin(), adv, hit, damage).withDice(dice);
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

    private RollMessage reactionRoll(Encounter e, PendingAttack a, EncounterParticipant att, CharacterSheet attSheet, Boost boost,
                                     EncounterParticipant tp, CharacterSheet tsh, ReactionType reaction, int attackers,
                                     Ctx ctx, Matchup mu, Calc counterCalc, UUID userId, ActorRole role) {
        boolean defend = reaction == ReactionType.DEFEND;
        List<RollPart> hit = new ArrayList<>();
        if (counterCalc != null) {
            hit.addAll(counterCalc.hit()); // Contragolpe: o teste é o acerto da técnica (já com Desgaste)
        } else {
            int mod = RollRules.modOrZero(defend ? tsh.getCon() : tsh.getDex());
            String attr = defend ? "CON" : "DES";
            if (!mu.defender().hasArt() || !mu.defender().reaction().contains(reaction)) {
                hit.add(new RollPart("HIT", "Fora da especialização: " + attr + "/2", SheetRules.half(mod), null, false));
            } else {
                hit.add(new RollPart("HIT", attr, mod, null, false));
            }
            int fatigue = CombatRules.fatiguePenalty(tsh.getPhysicalFatigue());
            if (fatigue > 0) hit.add(new RollPart("HIT", "Desgaste Físico", -fatigue, null, false));
        }
        int group = AttackRules.groupPenalty(attackers);
        if (group != 0) hit.add(new RollPart("HIT", "Luta contra Grupo", group, null, false));
        int timing = AttackRules.timingBonus(uses(a, att, tp));
        if (timing > 0) hit.add(new RollPart("HIT", "Timing", timing, null, false));
        Hysteria h = VitalRules.activeHysteria(tp.getAdrenaline(), tp.getHysteria());
        if (h == Hysteria.WALL) hit.add(new RollPart("HIT", "Muralha", 4, null, false));
        if (h == Hysteria.FURY) hit.add(new RollPart("HIT", "Fúria", -2, null, false));
        if (h == Hysteria.LIGHTNING) hit.add(new RollPart("HIT", "Relâmpago", 2, null, false));
        if (defend && mu.defender().hasArtNamed("Sumo")) hit.add(new RollPart("HIT", "Preso ao Solo +2", 2, null, false));
        if (reaction == ReactionType.DODGE && mu.defender().hasArtNamed("Capoeira")
                && martial.counterValue(tp.getId(), "RITMO", null) >= 1) {
            hit.add(new RollPart("HIT", "Ritmo +1", 1, null, false));
        }
        if (reaction == ReactionType.COUNTER && mu.defender().hasArtNamed("Karatê")
                && martial.counterValue(tp.getId(), "FOCO", null) > 0) {
            hit.add(new RollPart("HIT", "Foco (Zanshin) +2", 2, null, false));
        }
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
        hit.addAll(boost.parts());
        adv += boost.adv();
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
        calc = withMartial(e, calc, probe, tp, tsh, downed, martial.fighter(tp, tsh), tech, Inter.NONE);
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

    /** Golpes ATIVOS da ficha com o módulo Contragolpe (podem ser usados como Contra-ataque, §41). */
    private List<RollProfileService.CoreTechnique> counterStrikes(CharacterSheet sheet) {
        return profiles.coreTechniques(sheet, TechniqueCore.STRIKE).stream()
                .filter(x -> ModuleRules.has(x.items(), "Contragolpe")).toList();
    }

    /** Por que a técnica de Reação não pode ser usada agora (null = pode). */
    private String techReason(Ctx ctx, UUID targetId, CharacterSheet sheet, RollProfileService.CoreTechnique rt) {
        boolean strike = ModuleRules.has(rt.items(), "Contragolpe");
        ReactionType type = strike ? ReactionType.COUNTER : TechniqueRules.reaction(rt.items()).type();
        if (ctx.has(targetId, VitalService.EXHAUSTED)) return "Exausto: sem técnicas";
        if (type == ReactionType.COUNTER && ctx.has(targetId, STUNNED)) return "Atordoado: sem Contra-ataque";
        if (sheet.getCurrentSp() < rt.spCost()) return "SP insuficiente (" + sheet.getCurrentSp() + "/" + rt.spCost() + ")";
        return null;
    }

    /** Técnicas de Reação do alvo com o bônus previsto (o da reação comum do mesmo tipo + Reforço). */
    private List<AttackResponse.TechOption> techOptions(PendingAttack a, EncounterParticipant att, EncounterParticipant tp,
                                                       CharacterSheet tsh, Ctx ctx) {
        List<RollProfileService.CoreTechnique> list = new ArrayList<>(profiles.coreTechniques(tsh, TechniqueCore.REACTION));
        list.addAll(counterStrikes(tsh));
        if (list.isEmpty()) return List.of();
        Map<ReactionType, Integer> base = options(a, att, tp, tsh, ctx).stream()
                .collect(Collectors.toMap(Option::reaction, Option::predictedBonus));
        return list.stream().map(rt -> {
            String why = techReason(ctx, tp.getId(), tsh, rt);
            if (ModuleRules.has(rt.items(), "Contragolpe")) {
                return new AttackResponse.TechOption(rt.technique().getId(), rt.variation(), rt.name() + " (Contragolpe)",
                        ReactionType.COUNTER, why == null, why, base.getOrDefault(ReactionType.COUNTER, 0), rt.spCost(), List.of());
            }
            TechniqueRules.Reaction rr = TechniqueRules.reaction(rt.items());
            return new AttackResponse.TechOption(rt.technique().getId(), rt.variation(), rt.name(), rr.type(), why == null,
                    why, base.getOrDefault(rr.type(), 0) + rr.bonus(), rt.spCost(), rr.triggers());
        }).toList();
    }

    /** Parcelas e vantagem extras de uma reação: técnica de Reação e estado Preparado (§38). */
    private record Boost(List<RollPart> parts, int adv) {
        static final Boost NONE = new Boost(List.of(), 0);
    }

    /** Técnica de Golpe com Contragolpe escolhida como Contra-ataque deste alvo (null = Contra-ataque comum). */
    private RollProfileService.CoreTechnique counterTech(CharacterSheet rsh, AttackTarget t) {
        if (t.getReaction() != ReactionType.COUNTER || t.getReactionTechniqueId() == null || rsh == null) return null;
        return counterStrikes(rsh).stream()
                .filter(x -> x.technique().getId().equals(t.getReactionTechniqueId())
                        && java.util.Objects.equals(x.variation(), t.getReactionVariation())).findFirst().orElse(null);
    }

    /** Perfil do Golpe com Contragolpe escolhido como Contra-ataque deste alvo (null = Contra-ataque comum). */
    private Calc counterCalc(CharacterSheet rsh, EncounterParticipant rp, AttackTarget t, Ctx ctx) {
        RollProfileService.CoreTechnique ct = counterTech(rsh, t);
        if (ct == null) return null;
        var o = new RollProfileService.Opts(martial.validBase(rsh, rp.getActiveBaseId()), knocked(ctx, rp), null, null,
                martial.attackType(martial.fighter(rp, rsh), ct.technique()));
        return withAdrenaline(profiles.technique(rsh, ct.technique(), ct.variation(), o), rp, rsh);
    }

    /** Técnica de Reação escolhida para este alvo (null = reação comum). */
    private RollProfileService.CoreTechnique reactionTechnique(CharacterSheet sheet, AttackTarget t) {
        if (t.getReactionTechniqueId() == null || sheet == null) return null;
        return profiles.coreTechniques(sheet, TechniqueCore.REACTION).stream()
                .filter(x -> x.technique().getId().equals(t.getReactionTechniqueId())
                        && java.util.Objects.equals(x.variation(), t.getReactionVariation()))
                .findFirst().orElse(null);
    }

    /** Reforço da técnica (Esboço: desvantagem), Reforço e Leitura da Preparação ativa (Leitura é consumida). */
    private Boost reactionBoost(Encounter e, EncounterParticipant rp, CharacterSheet rsh,
                                RollProfileService.CoreTechnique rt, ReactionType reaction, WeaponCatalog.Def attackerWeapon,
                                BasicAttack basic) {
        List<RollPart> parts = new ArrayList<>();
        int adv = 0;
        if (reaction == ReactionType.DEFEND && equipment.wears(rsh, "BOXING_GLOVES")) {
            parts.add(new RollPart("HIT", "Luvas de Boxe", 2, null, false));
        }
        if (reaction == ReactionType.DEFEND && basic == BasicAttack.CHUTE && equipment.wears(rsh, "SHIN_GUARDS")) {
            parts.add(new RollPart("HIT", "Caneleiras", 1, null, false));
        }
        if (reaction == ReactionType.DODGE && "HEAVY".equals(armorWeight(rsh))) {
            adv--;
            parts.add(new RollPart("HIT", "Proteção Pesada (desvantagem)", 0, null, false));
        }
        WeaponCatalog.Def dw = defenderWeapon(rsh);
        if (reaction == ReactionType.DEFEND && dw != null && dw.defensive()) {
            parts.add(new RollPart("HIT", "Arma Defensiva (" + dw.name() + ")", 2, null, false));
        }
        if (attackerWeapon != null && attackerWeapon.firearm()) {
            adv--; // ninguém lê uma bala
            parts.add(new RollPart("HIT", "Disparo de arma de fogo (desvantagem)", 0, null, false));
        }
        if (reaction == ReactionType.DODGE && vital.hasCondition(rp, ARC_WEIGHT)) {
            adv--;
            parts.add(new RollPart("HIT", "Peso do arco (desvantagem)", 0, null, false));
        }
        if (rt != null) {
            TechniqueRules.Reaction rr = TechniqueRules.reaction(rt.items());
            if (rr.bonus() != 0) parts.add(new RollPart("HIT", "Reforço (" + rt.name() + ")", rr.bonus(), null, false));
            if (rt.sketch()) adv--;
        }
        Prepared pr = prepared(rp, rsh);
        if (pr != null) {
            int bonus = pr.prep().reinforce().getOrDefault(reaction, 0);
            if (bonus != 0) parts.add(new RollPart("HIT", "Reforço (preparado: " + pr.tech().name() + ")", bonus, null, false));
            if (pr.prep().reading() && pr.flags().contains("L")) {
                adv++;
                consume(pr, "L");
                log(e, rp.getId(), rp.getName() + " lê o golpe (Leitura): vantagem na reação");
            }
        }
        return parts.isEmpty() && adv == 0 ? Boost.NONE : new Boost(parts, adv);
    }

    /**
     * Depois de uma reação com técnica bem-sucedida: Bloqueio Punitivo (1d4 + FOR no atacante ao Defender), Efeitos da
     * técnica no atacante (resistidos contra o teste da reação), Abertura (vantagem no próximo ataque contra ele) e
     * Fôlego Tático (SP = mod. de CON).
     */
    private void reactionAftermath(Encounter e, RollProfileService.CoreTechnique rt, TechniqueRules.Reaction rr,
                                   AttackOutcome outcome, EncounterParticipant rp, CharacterSheet rsh,
                                   EncounterParticipant att, CharacterSheet attSheet, RollMessage rmsg, UUID userId,
                                   ActorRole role) {
        VitalService.Ctx c = VitalService.Ctx.of(userId, role, "Técnica: " + rt.name());
        if (rr.punitive() && outcome == AttackOutcome.DEFENDED) {
            int d = Math.max(0, rolls.rollInternal(rsh, "1d4", "Bloqueio Punitivo: " + rp.getName() + " → " + att.getName(),
                    userId, role).getTotal() + RollRules.modOrZero(rsh.getStr()));
            log(e, att.getId(), att.getName() + " sofre " + d + " de dano no membro que atacou (Bloqueio Punitivo)");
            attacksAttacker(e, att, attSheet, d, c);
        }
        BaseRules.Profile ab = profiles.baseProfile(attSheet, att.getActiveBaseId());
        for (AssemblyItem it : rt.items()) {
            if (it.getKind() != AssemblyItemKind.EFFECT) continue;
            EffectRules.Effect fx = EffectRules.of(it.getModule()).orElse(null);
            if (fx == null) {
                log(e, att.getId(), "Efeito " + it.getModule() + " de " + rp.getName() + " em " + att.getName() + ": aplicar manualmente");
                continue;
            }
            if (fx.resist() != null && resists(e, att, attSheet, fx, it.getModule(), rmsg.total(), ab, userId, role)) continue;
            applyEffect(e, fx, it, false, rp, rsh, att, attSheet, c, userId, role);
        }
        if (rr.opening()) {
            vital.addEffectCondition(e, rp, "ABERTURA", ConditionExpiry.END_OF_TARGET_TURN, rt.name(), att.getId());
        }
        if (rr.breath()) vital.applySp(e, rp, rsh, Math.max(0, RollRules.modOrZero(rsh.getCon())), c);
    }

    /** Estado Preparado (técnica de Preparação ativa): a técnica, os números dela e o que ainda não foi consumido. */
    private record Prepared(CombatCondition cond, RollProfileService.CoreTechnique tech, TechniqueRules.Preparation prep,
                            String flags) {}

    /** Lê a condição PREPARADO ("techniqueId;variação;flags"); null sem preparação válida. */
    private Prepared prepared(EncounterParticipant p, CharacterSheet s) {
        CombatCondition k = conditions.findByParticipantIdIn(List.of(p.getId())).stream()
                .filter(c -> "PREPARADO".equals(c.getConditionKey()) && c.getNote() != null).findFirst().orElse(null);
        if (k == null || s == null) return null;
        String[] f = k.getNote().split(";", -1);
        if (f.length < 3) return null;
        RollProfileService.CoreTechnique tech = profiles.coreTechniques(s, TechniqueCore.PREPARATION).stream()
                .filter(x -> x.technique().getId().toString().equals(f[0])
                        && java.util.Objects.equals(x.variation() == null ? "" : x.variation(), f[1]))
                .findFirst().orElse(null);
        return tech == null ? null : new Prepared(k, tech, TechniqueRules.preparation(tech.items()), f[2]);
    }

    private void consume(Prepared pr, String flag) {
        String[] f = pr.cond().getNote().split(";", -1);
        pr.cond().setNote(f[0] + ";" + f[1] + ";" + f[2].replace(flag, ""));
        conditions.save(pr.cond());
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

    private Matchup guardianMatchup(PendingAttack a, MartialService.Fighter fa, CharacterTechnique tech,
                                    EncounterParticipant att, CharacterSheet attSheet, EncounterParticipant gp,
                                    CharacterSheet gsh) {
        MartialService.Fighter fd = martial.fighter(gp, gsh);
        return new Matchup(fd, interaction(a, fa, tech, att, attSheet, fd, gp, gsh));
    }

    /** Tipo marcial do golpe: arte da técnica, tipo da arma em mãos (se golpe armado sem arte), senão a arte principal (§31). */
    private MartialType attackTypeOf(PendingAttack a, EncounterParticipant att, CharacterSheet attSheet) {
        if (a.getSourceType() == RollSourceType.BASIC_ATTACK && a.getBasicAttack() == BasicAttack.ARMA) {
            InventoryItem it = attSheet.getInventory().stream().filter(i -> i.getId().equals(a.getSourceId())).findFirst().orElse(null);
            MartialType wt = it == null ? null : weapons.find(it.getWeaponKey()).map(w -> MartialRules.type(w.martialType())).orElse(null);
            if (wt != null) return wt;
        }
        CharacterTechnique t = a.getSourceType() == RollSourceType.TECHNIQUE ? technique(attSheet, a) : null;
        if (t != null && t.getCharacterArt() == null && profiles.isArmed(attSheet, t)) {
            MartialType wt = RollProfileService.inHand(attSheet).stream()
                    .map(i -> weapons.find(i.getWeaponKey()).orElse(null))
                    .filter(w -> w != null && !w.augment())
                    .map(w -> MartialRules.type(w.martialType())).filter(java.util.Objects::nonNull)
                    .findFirst().orElse(null);
            if (wt != null) return wt;
        }
        return martial.attackType(martial.fighter(att, attSheet), t);
    }

    private Calc calcFor(CharacterSheet s, PendingAttack a, boolean downed, EncounterParticipant att, CharacterSheet attSheet) {
        var o = new RollProfileService.Opts(martial.validBase(attSheet, att.getActiveBaseId()), downed, a.getRegion(),
                a.getRegionPenalty(), attackTypeOf(a, att, attSheet));
        Calc c;
        if (a.getSourceType() == RollSourceType.TECHNIQUE) c = profiles.technique(s, technique(s, a), a.getVariation(), o);
        else {
            if (a.getBasicAttack() == null) throw new InvalidCombatException("Informe o ataque básico");
            c = a.getBasicAttack() == BasicAttack.ARMA ? profiles.weaponOf(s, a.getSourceId(), a.getVariation(), o)
                    : profiles.basic(s, a.getBasicAttack(), o);
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
        return c.with(sp, VitalRules.critMargin(tier), c.advantage(), hit, damage);
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
                && calc.dice().terms().stream().anyMatch(t -> p.source().startsWith(t.count() * 2 + "d" + t.sides() + " ")
                        && p.dice().stream().anyMatch(d -> d == t.sides())));
    }

    /** Rola todos os termos de `dice` (sem dobrar) e devolve a soma. */
    private int rollDice(CharacterSheet s, RollRules.Dice dice, String purpose, UUID userId, ActorRole role) {
        int sum = 0;
        for (RollRules.Dice t : dice.terms()) sum += rolls.rollInternal(s, t.text(), purpose, userId, role).getTotal();
        return sum;
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
