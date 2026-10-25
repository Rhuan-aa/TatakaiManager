package com.tatakai.manager.combat.service;

import com.tatakai.manager.combat.dto.response.EncounterResponse.ConditionResponse;
import com.tatakai.manager.combat.dto.response.EncounterResponse.CounterResponse;
import com.tatakai.manager.combat.dto.response.EncounterResponse.ParticipantResponse;
import com.tatakai.manager.combat.dto.response.EncounterResponse.Resources;

import com.tatakai.manager.build.entity.BuildStatus;
import com.tatakai.manager.campaign.entity.OptionalRule;
import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.campaign.service.CampaignRuleService;
import com.tatakai.manager.catalog.entity.TechniqueCore;
import com.tatakai.manager.catalog.service.ArtCounterCatalog;
import com.tatakai.manager.catalog.service.ConditionCatalog;
import com.tatakai.manager.catalog.service.ConsumableCatalog;
import com.tatakai.manager.catalog.service.EquipmentCatalog;
import com.tatakai.manager.catalog.service.WeaponCatalog;
import com.tatakai.manager.combat.dto.request.AddConditionRequest;
import com.tatakai.manager.combat.dto.request.CombatActionRequest;
import com.tatakai.manager.combat.dto.request.GrappleRequest;
import com.tatakai.manager.combat.dto.request.SetHysteriaRequest;
import com.tatakai.manager.combat.dto.request.UpdateEncounterRequest;
import com.tatakai.manager.combat.dto.request.UpdateParticipantRequest;
import com.tatakai.manager.combat.dto.request.VitalAdjustRequest;
import com.tatakai.manager.combat.dto.response.CombatEventResponse;
import com.tatakai.manager.combat.dto.response.CombatUpdateMessage;
import com.tatakai.manager.combat.dto.response.EncounterResponse;
import com.tatakai.manager.combat.dto.response.EncounterSummaryResponse;
import com.tatakai.manager.combat.entity.AttackStatus;
import com.tatakai.manager.combat.entity.CombatAction;
import com.tatakai.manager.combat.entity.CombatClassification;
import com.tatakai.manager.combat.entity.CombatCondition;
import com.tatakai.manager.combat.entity.CombatCounter;
import com.tatakai.manager.combat.entity.CombatEvent;
import com.tatakai.manager.combat.entity.CombatEventType;
import com.tatakai.manager.combat.entity.ConditionExpiry;
import com.tatakai.manager.combat.entity.Encounter;
import com.tatakai.manager.combat.entity.EncounterParticipant;
import com.tatakai.manager.combat.entity.EncounterStatus;
import com.tatakai.manager.combat.entity.Hysteria;
import com.tatakai.manager.combat.exception.CombatConflictException;
import com.tatakai.manager.combat.exception.EncounterNotFoundException;
import com.tatakai.manager.combat.exception.InvalidCombatException;
import com.tatakai.manager.combat.repository.CombatConditionRepository;
import com.tatakai.manager.combat.repository.CombatEventRepository;
import com.tatakai.manager.combat.repository.EncounterParticipantRepository;
import com.tatakai.manager.combat.repository.EncounterRepository;
import com.tatakai.manager.combat.repository.PendingAttackRepository;
import com.tatakai.manager.combat.service.rules.BaseRules;
import com.tatakai.manager.combat.service.rules.CombatRules;
import com.tatakai.manager.combat.service.rules.EffectRules;
import com.tatakai.manager.combat.service.rules.EquipmentRules;
import com.tatakai.manager.combat.service.rules.GrappleRules;
import com.tatakai.manager.combat.service.rules.MartialRules;
import com.tatakai.manager.combat.service.rules.TechniqueRules;
import com.tatakai.manager.combat.service.rules.VitalRules;
import com.tatakai.manager.combat.service.rules.WeaponRules;
import com.tatakai.manager.martial.entity.CharacterArtStatus;
import com.tatakai.manager.martial.entity.CharacterTechnique;
import com.tatakai.manager.martial.repository.CharacterArtRepository;
import com.tatakai.manager.npc.repository.CampaignNpcRepository;
import com.tatakai.manager.roll.entity.RollPart;
import com.tatakai.manager.roll.service.RollProfileService;
import com.tatakai.manager.roll.service.RollService;
import com.tatakai.manager.roll.service.rules.RollRules;
import com.tatakai.manager.shared.exception.AccessDeniedException;
import com.tatakai.manager.sheet.dto.response.InventoryItemResponse;
import com.tatakai.manager.sheet.entity.ActorRole;
import com.tatakai.manager.sheet.entity.Attribute;
import com.tatakai.manager.sheet.entity.CharacterSheet;
import com.tatakai.manager.sheet.entity.Dominance;
import com.tatakai.manager.sheet.entity.InventoryItem;
import com.tatakai.manager.sheet.entity.ItemLocation;
import com.tatakai.manager.sheet.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.sheet.repository.CharacterSheetRepository;
import com.tatakai.manager.sheet.service.SheetActivityLogService;
import com.tatakai.manager.sheet.service.rules.SheetRules;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Motor de combate F6a: encontro, iniciativa, turnos/rodadas, PA, ações, condições e linha do tempo. */
@Service
@Transactional
public class CombatService {

    private static final String STUNNED = "ATORDOADO";
    private static final String KNOCKED_DOWN = "DERRUBADO";
    private static final String GRABBED = "AGARRADO";
    private static final String RESTRAINED = "IMOBILIZADO";
    private static final String BASE_BROKEN = "QUEBRA_DE_BASE";

    private final EncounterRepository encounters;
    private final EncounterParticipantRepository participants;
    private final CombatConditionRepository conditions;
    private final CombatEventRepository timeline;
    private final CharacterSheetRepository sheets;
    private final CharacterArtRepository arts;
    private final CampaignNpcRepository campaignNpcs;
    private final CampaignMemberRepository members;
    private final RollService rolls;
    private final SheetActivityLogService activityLog;
    private final ConditionCatalog catalog;
    private final ApplicationEventPublisher publisher;
    private final PendingAttackRepository attacks;
    private final VitalService vital;
    private final MartialService martial;
    private final CampaignRuleService rules;
    private final RollProfileService profiles;
    private final Random random = new SecureRandom();

    /** Catálogos estáticos de armas, equipamentos e consumíveis (só leitura do classpath). */
    private final WeaponCatalog weapons = new WeaponCatalog();
    private final ConsumableCatalog consumables = new ConsumableCatalog();
    private final EquipmentCatalog equipment = new EquipmentCatalog();

    public CombatService(EncounterRepository encounters, EncounterParticipantRepository participants,
                         CombatConditionRepository conditions, CombatEventRepository timeline,
                         CharacterSheetRepository sheets, CharacterArtRepository arts,
                         CampaignNpcRepository campaignNpcs, CampaignMemberRepository members,
                         RollService rolls, SheetActivityLogService activityLog, ConditionCatalog catalog,
                         ApplicationEventPublisher publisher, PendingAttackRepository attacks,
                         VitalService vital, MartialService martial, CampaignRuleService rules,
                         RollProfileService profiles) {
        this.profiles = profiles;
        this.rules = rules;
        this.encounters = encounters;
        this.participants = participants;
        this.conditions = conditions;
        this.timeline = timeline;
        this.sheets = sheets;
        this.arts = arts;
        this.campaignNpcs = campaignNpcs;
        this.members = members;
        this.rolls = rolls;
        this.activityLog = activityLog;
        this.catalog = catalog;
        this.publisher = publisher;
        this.attacks = attacks;
        this.vital = vital;
        this.martial = martial;
    }

    // ---------- acesso ----------

    private Role requireMembership(UUID campaignId, UUID userId) {
        return members.findByCampaignIdAndUserId(campaignId, userId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha")).getRole();
    }

    private void requireMaster(UUID campaignId, UUID userId) {
        if (!members.existsByCampaignIdAndUserIdAndRole(campaignId, userId, Role.MASTER)) {
            throw new AccessDeniedException("Apenas o Mestre da campanha pode fazer isso");
        }
    }

    private Encounter load(UUID encounterId) {
        return encounters.findById(encounterId).orElseThrow(() -> new EncounterNotFoundException("Encontro não encontrado"));
    }

    private Encounter loadAsMaster(UUID encounterId, UUID userId) {
        Encounter e = load(encounterId);
        requireMaster(e.getCampaignId(), userId);
        return e;
    }

    private static void requireNotEnded(Encounter e) {
        if (e.getStatus() == EncounterStatus.ENDED) throw new CombatConflictException("O encontro já foi encerrado");
    }

    private static void requireActive(Encounter e) {
        if (e.getStatus() != EncounterStatus.ACTIVE) throw new CombatConflictException("O encontro não está em andamento");
    }

    private EncounterParticipant participantOf(Encounter e, UUID participantId) {
        return participants.findByEncounterIdOrderByOrderIndex(e.getId()).stream()
                .filter(p -> p.getId().equals(participantId)).findFirst()
                .orElseThrow(() -> new EncounterNotFoundException("Participante não encontrado"));
    }

    /** Dono do jogador da ficha; null para ficha de NPC (quem age é o Mestre). */
    private static UUID ownerUserId(CharacterSheet s) {
        return s.isNpcSheet() ? null : s.getCampaignMember().getUser().getId();
    }

    // ---------- consultas ----------

    @Transactional(readOnly = true)
    public List<EncounterSummaryResponse> list(UUID campaignId, UUID userId) {
        requireMembership(campaignId, userId);
        return encounters.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream()
                .map(e -> new EncounterSummaryResponse(e.getId(), e.getName(), e.getStatus(), e.getRound(), e.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public EncounterResponse get(UUID encounterId, UUID userId) {
        Encounter e = load(encounterId);
        Role role = requireMembership(e.getCampaignId(), userId);
        return view(e, userId, role == Role.MASTER);
    }

    @Transactional(readOnly = true)
    public List<CombatEventResponse> events(UUID encounterId, UUID userId) {
        Encounter e = load(encounterId);
        requireMembership(e.getCampaignId(), userId);
        return timeline.findByEncounterIdOrderByCreatedAtAsc(e.getId()).stream()
                .map(x -> new CombatEventResponse(x.getId(), x.getRound(), x.getTurnCounter(), x.getParticipantId(),
                        x.getType(), x.getText(), x.getCreatedAt()))
                .toList();
    }

    private EncounterResponse view(Encounter e, UUID userId, boolean master) {
        List<EncounterParticipant> ps = participants.findByEncounterIdOrderByOrderIndex(e.getId());
        List<UUID> ids = ps.stream().map(EncounterParticipant::getId).toList();
        Map<UUID, CharacterSheet> sheetById = sheets.findAllById(ps.stream().map(EncounterParticipant::getSheetId).toList())
                .stream().collect(Collectors.toMap(CharacterSheet::getId, Function.identity()));
        Map<UUID, List<CombatCondition>> condById = ids.isEmpty() ? Map.of()
                : conditions.findByParticipantIdIn(ids).stream()
                        .collect(Collectors.groupingBy(CombatCondition::getParticipantId));
        Map<UUID, List<CombatCounter>> counterById = martial.countersOf(ids).stream()
                .collect(Collectors.groupingBy(CombatCounter::getParticipantId));
        List<ParticipantResponse> out = new ArrayList<>();
        for (EncounterParticipant p : ps) {
            CharacterSheet s = sheetById.get(p.getSheetId());
            UUID owner = s == null ? null : ownerUserId(s);
            boolean seeResources = s != null && (master || (owner != null && owner.equals(userId)));
            Resources res = seeResources ? new Resources(s.getCurrentHp(), s.getHpMax(), s.getCurrentSp(),
                    s.getSpMax(), s.getCurrentKp(), s.getKpMax(), s.getPhysicalFatigue(), s.getMentalFatigue()) : null;
            MartialService.Info m = s == null ? null : martial.info(p, s, seeResources);
            List<ConditionResponse> cs = condById.getOrDefault(p.getId(), List.of()).stream()
                    .map(c -> new ConditionResponse(c.getId(), c.getConditionKey(),
                            catalog.find(c.getConditionKey()).map(ConditionCatalog.Def::name).orElse(c.getConditionKey()),
                            c.getExpiresAt(), c.getTurnsRemaining(), c.getIntensity(), c.getSourceParticipantId(),
                            c.getNote()))
                    .toList();
            out.add(new ParticipantResponse(p.getId(), p.getSheetId(), p.getName(), p.isNpc(), p.getInitiative(),
                    p.getOrderIndex(), p.getPaCurrent(), p.getPaBonusNext(), p.getPaLossNext(), p.getMovesThisTurn(),
                    owner, res, cs, p.getClassification(), p.getTeam(), p.getAdrenaline(),
                    VitalRules.tier(p.getAdrenaline()).name(), p.getHysteria(), p.getRoundsAtApex(),
                    s == null ? 0 : s.getOpenWounds(), p.getDeathSuccesses(), p.getDeathFailures(),
                    cs.stream().anyMatch(x -> VitalService.EXHAUSTED.equals(x.key())),
                    m == null ? null : m.mainArtId(), m == null ? null : m.mainArtName(), m == null ? null : m.type(),
                    p.getGuard(), s == null ? null : MartialRules.leadFoot(s.getDominance(), p.getGuard()),
                    m == null ? null : m.activeBaseId(), m == null ? null : m.activeBaseName(),
                    counterById.getOrDefault(p.getId(), List.of()).stream().map(this::counterView).toList(),
                    m == null ? null : m.arts(), m == null ? null : m.bases(),
                    seeResources ? profiles.coreTechniques(s, TechniqueCore.PREPARATION).stream()
                            .map(t -> new EncounterResponse.TechRef(t.technique().getId(), t.variation(), t.name(), t.spCost()))
                            .toList() : null,
                    s == null ? List.of() : s.getInventory().stream()
                            .filter(i -> i.getWeaponKey() != null || i.getEquipmentKey() != null || i.getConsumableKey() != null)
                            .filter(i -> seeResources || i.getLocation() == ItemLocation.HAND || i.getLocation() == ItemLocation.WORN)
                            .map(com.tatakai.manager.sheet.dto.response.InventoryItemResponse::of).toList()));
        }
        UUID current = e.getStatus() == EncounterStatus.ACTIVE && e.getCurrentIndex() < ps.size()
                ? ps.get(e.getCurrentIndex()).getId() : null;
        return new EncounterResponse(e.getId(), e.getCampaignId(), e.getName(), e.getStatus(), e.getRound(),
                e.getTurnCounter(), current, out, e.getDefaultClassification(), e.getEndedAt(), e.getFloor());
    }

    private CounterResponse counterView(CombatCounter c) {
        ArtCounterCatalog.Def d = martial.counterDef(c.getCounterKey());
        return new CounterResponse(c.getId(), c.getCounterKey(), d == null ? c.getCounterKey() : d.name(), c.getValue(),
                d == null ? 0 : d.min(), d == null ? 99 : d.max(), c.getTargetParticipantId());
    }

    private EncounterResponse done(Encounter e, String type, UUID userId, boolean master) {
        publisher.publishEvent(new CombatUpdateMessage(e.getCampaignId(), e.getId(), type));
        return view(e, userId, master);
    }

    private void log(Encounter e, UUID participantId, CombatEventType type, String text) {
        timeline.save(CombatEvent.builder().encounterId(e.getId()).round(e.getRound()).turnCounter(e.getTurnCounter())
                .participantId(participantId).type(type)
                .text(text.length() > 500 ? text.substring(0, 500) : text).build());
    }

    // ---------- encontro ----------

    public EncounterResponse create(UUID campaignId, String name, UUID userId) {
        return create(campaignId, name, null, userId);
    }

    public EncounterResponse create(UUID campaignId, String name, CombatClassification classification, UUID userId) {
        requireMaster(campaignId, userId);
        if (encounters.existsByCampaignIdAndStatusNot(campaignId, EncounterStatus.ENDED)) {
            throw new CombatConflictException("Já existe um encontro não encerrado nesta campanha");
        }
        Encounter e = encounters.save(Encounter.builder().campaignId(campaignId).name(name.trim())
                .status(EncounterStatus.SETUP)
                .defaultClassification(classification == null ? CombatClassification.REAL : classification).build());
        return done(e, "CREATED", userId, true);
    }

    public EncounterResponse updateEncounter(UUID encounterId, UpdateEncounterRequest req, UUID userId) {
        Encounter e = loadAsMaster(encounterId, userId);
        requireNotEnded(e);
        if (req.name() != null && !req.name().isBlank()) e.setName(req.name().trim());
        if (req.defaultClassification() != null) e.setDefaultClassification(req.defaultClassification());
        if (req.floor() != null) e.setFloor(req.floor());
        encounters.save(e);
        return done(e, "UPDATED", userId, true);
    }

    public EncounterResponse addParticipant(UUID encounterId, UUID sheetId, UUID userId) {
        Encounter e = loadAsMaster(encounterId, userId);
        requireNotEnded(e);
        CharacterSheet s = sheets.findById(sheetId).orElseThrow(CharacterSheetNotFoundException::new);
        if (s.isNpcSheet()) {
            if (!campaignNpcs.existsByCampaignIdAndNpcId(e.getCampaignId(), s.getNpcId())) {
                throw new InvalidCombatException("O NPC desta ficha não está associado à campanha");
            }
        } else {
            if (!e.getCampaignId().equals(s.getCampaignMember().getCampaign().getId())) {
                throw new InvalidCombatException("A ficha não pertence a esta campanha");
            }
            if (s.getBuildStatus() != BuildStatus.COMPLETED) {
                throw new InvalidCombatException("Apenas fichas concluídas podem entrar no combate");
            }
        }
        if (participants.existsByEncounterIdAndSheetId(e.getId(), s.getId())) {
            throw new CombatConflictException("Esta ficha já está no encontro");
        }
        int next = participants.findByEncounterIdOrderByOrderIndex(e.getId()).size();
        EncounterParticipant np = participants.save(EncounterParticipant.builder().encounterId(e.getId())
                .sheetId(s.getId()).name(s.getName()).npc(s.isNpcSheet()).orderIndex(next)
                .classification(e.getDefaultClassification()).team(s.isNpcSheet() ? 2 : 1).build());
        if (e.getStatus() == EncounterStatus.ACTIVE) vital.syncDissociation(e, np, s);
        ensureCounters(e);
        return done(e, "PARTICIPANT_ADDED", userId, true);
    }

    public EncounterResponse removeParticipant(UUID encounterId, UUID participantId, UUID userId) {
        Encounter e = loadAsMaster(encounterId, userId);
        requireNotEnded(e);
        List<EncounterParticipant> ps = new ArrayList<>(participants.findByEncounterIdOrderByOrderIndex(e.getId()));
        EncounterParticipant p = participantOf(e, participantId);
        int idx = ps.indexOf(p);
        if (e.getStatus() == EncounterStatus.ACTIVE && idx == e.getCurrentIndex()) {
            throw new CombatConflictException("Avance o turno antes de remover o participante atual");
        }
        conditions.deleteAll(conditions.findByParticipantIdIn(List.of(p.getId())));
        participants.delete(p);
        ps.remove(idx);
        reindex(ps);
        if (e.getStatus() == EncounterStatus.ACTIVE && idx < e.getCurrentIndex()) {
            e.setCurrentIndex(e.getCurrentIndex() - 1);
        }
        return done(e, "PARTICIPANT_REMOVED", userId, true);
    }

    /** Contadores de arte (F6d) de todos os participantes; idempotente. */
    private void ensureCounters(Encounter e) {
        List<EncounterParticipant> ps = participants.findByEncounterIdOrderByOrderIndex(e.getId());
        Map<UUID, CharacterSheet> byId = new HashMap<>();
        for (EncounterParticipant p : ps) byId.put(p.getId(), sheetOf(p));
        martial.ensureAll(ps, byId);
    }

    private static void reindex(List<EncounterParticipant> ps) {
        for (int i = 0; i < ps.size(); i++) ps.get(i).setOrderIndex(i);
    }

    private int rollInitiative(Encounter e, CharacterSheet s, UUID userId) {
        boolean art = arts.findBySheetIdOrderByCreatedAt(s.getId()).stream()
                .anyMatch(a -> a.getStatus() == CharacterArtStatus.ACTIVE);
        int bonus = CombatRules.initiativeBonus(s.getDex(), art, s.getPhysicalFatigue());
        return rolls.rollInternal(s, CombatRules.initiativeExpression(bonus), "Iniciativa — " + e.getName(),
                userId, ActorRole.MASTER).getTotal();
    }

    private CharacterSheet sheetOf(EncounterParticipant p) {
        return sheets.findById(p.getSheetId()).orElseThrow(CharacterSheetNotFoundException::new);
    }

    public EncounterResponse start(UUID encounterId, UUID userId) {
        Encounter e = loadAsMaster(encounterId, userId);
        if (e.getStatus() != EncounterStatus.SETUP) throw new CombatConflictException("O encontro já foi iniciado");
        List<EncounterParticipant> ps = new ArrayList<>(participants.findByEncounterIdOrderByOrderIndex(e.getId()));
        if (ps.isEmpty()) throw new InvalidCombatException("Adicione ao menos um participante");
        Map<UUID, CharacterSheet> sheetById = new HashMap<>();
        for (EncounterParticipant p : ps) {
            CharacterSheet s = sheetOf(p);
            sheetById.put(p.getId(), s);
            vital.syncDissociation(e, p, s);
            if (s.isKnockoutSequel() && e.getCampaignId() != null
                    && rules.isEnabled(e.getCampaignId(), OptionalRule.KNOCKOUT_SEQUEL)) {
                vital.knockoutSequel(e, p, s, VitalService.Ctx.of(userId, ActorRole.MASTER, "Sequela de Nocaute"));
            }
            if (p.getInitiative() == null) {
                p.setInitiative(rollInitiative(e, s, userId));
                log(e, p.getId(), CombatEventType.INITIATIVE, p.getName() + " rolou iniciativa: " + p.getInitiative());
            }
        }
        CombatRules.sortOrder(ps, EncounterParticipant::getInitiative, p -> sheetById.get(p.getId()).getDex(), random);
        // Dissociação (RN-DESG-02): iniciativa vai para o fim (sort estável)
        ps.sort(java.util.Comparator.comparingInt(
                p -> VitalRules.dissociated(sheetById.get(p.getId()).getMentalFatigue()) ? 1 : 0));
        reindex(ps);
        e.setStatus(EncounterStatus.ACTIVE);
        e.setRound(1);
        e.setCurrentIndex(0);
        martial.ensureAll(ps, new HashMap<>(sheetById));
        log(e, null, CombatEventType.START, "Combate iniciado: " + e.getName());
        beginTurn(e, ps.get(0), ps, VitalService.Ctx.of(userId, ActorRole.MASTER, "Combate"));
        return done(e, "START", userId, true);
    }

    public EncounterResponse rerollInitiative(UUID encounterId, UUID participantId, UUID userId) {
        Encounter e = loadAsMaster(encounterId, userId);
        requireNotEnded(e);
        EncounterParticipant p = participantOf(e, participantId);
        p.setInitiative(rollInitiative(e, sheetOf(p), userId));
        log(e, p.getId(), CombatEventType.INITIATIVE, p.getName() + " rerrolou iniciativa: " + p.getInitiative());
        return done(e, "INITIATIVE", userId, true);
    }

    public EncounterResponse updateParticipant(UUID encounterId, UUID participantId, UpdateParticipantRequest req,
                                               UUID userId) {
        Encounter e = load(encounterId);
        requireNotEnded(e);
        List<EncounterParticipant> ps = new ArrayList<>(participants.findByEncounterIdOrderByOrderIndex(e.getId()));
        EncounterParticipant p = participantOf(e, participantId);
        if (!members.existsByCampaignIdAndUserIdAndRole(e.getCampaignId(), userId, Role.MASTER)) {
            // o dono do personagem só pode escolher a própria arte principal
            requireMembership(e.getCampaignId(), userId);
            boolean onlyMainArt = req.mainArtId() != null && req.initiative() == null && req.paCurrent() == null
                    && req.paBonusNext() == null && req.paLossNext() == null && req.orderIndex() == null
                    && req.classification() == null && req.team() == null && req.adrenaline() == null;
            if (!onlyMainArt || !userId.equals(ownerUserId(sheetOf(p)))) {
                throw new AccessDeniedException("Apenas o Mestre da campanha pode fazer isso");
            }
        }
        StringBuilder what = new StringBuilder();
        if (req.mainArtId() != null) {
            martial.requireActiveArt(sheetOf(p), req.mainArtId());
            p.setMainArtId(req.mainArtId());
            what.append(" arte principal");
        }
        if (req.initiative() != null) { p.setInitiative(req.initiative()); what.append(" iniciativa=").append(req.initiative()); }
        if (req.paCurrent() != null) { p.setPaCurrent(req.paCurrent()); what.append(" PA=").append(req.paCurrent()); }
        if (req.paBonusNext() != null) { p.setPaBonusNext(req.paBonusNext()); what.append(" PA+próx=").append(req.paBonusNext()); }
        if (req.paLossNext() != null) { p.setPaLossNext(req.paLossNext()); what.append(" PA−próx=").append(req.paLossNext()); }
        if (req.classification() != null) { p.setClassification(req.classification()); what.append(" classificação=").append(req.classification()); }
        if (req.team() != null) { p.setTeam(req.team()); what.append(" time=").append(req.team()); }
        if (req.adrenaline() != null) { vital.setAdrenaline(p, req.adrenaline()); what.append(" adrenalina=").append(req.adrenaline()); }
        if (req.orderIndex() != null) {
            UUID currentId = e.getStatus() == EncounterStatus.ACTIVE && e.getCurrentIndex() < ps.size()
                    ? ps.get(e.getCurrentIndex()).getId() : null;
            ps.remove(p);
            ps.add(Math.min(req.orderIndex(), ps.size()), p);
            reindex(ps);
            if (currentId != null) {
                for (int i = 0; i < ps.size(); i++) if (ps.get(i).getId().equals(currentId)) e.setCurrentIndex(i);
            }
            what.append(" posição=").append(req.orderIndex());
        }
        if (what.length() > 0) log(e, p.getId(), CombatEventType.GM, "Mestre ajustou " + p.getName() + ":" + what);
        return done(e, "GM", userId, true);
    }

    // ---------- ações e turnos ----------

    public EncounterResponse act(UUID encounterId, CombatActionRequest req, UUID userId) {
        Encounter e = load(encounterId);
        Role role = requireMembership(e.getCampaignId(), userId);
        boolean master = role == Role.MASTER;
        requireActive(e);
        EncounterParticipant p = participantOf(e, req.participantId());
        CharacterSheet s = sheetOf(p);
        if (!master && !userId.equals(ownerUserId(s))) {
            throw new AccessDeniedException("Você só pode agir pelo seu próprio personagem");
        }
        List<EncounterParticipant> ps = participants.findByEncounterIdOrderByOrderIndex(e.getId());
        if (!ps.get(e.getCurrentIndex()).getId().equals(p.getId())) {
            throw new InvalidCombatException("Não é o turno de " + p.getName());
        }
        CombatAction action = req.action();
        List<CombatCondition> mine = conditions.findByParticipantIdIn(List.of(p.getId()));
        java.util.Set<String> keys = mine.stream().map(CombatCondition::getConditionKey).collect(Collectors.toSet());
        if (keys.contains(VitalService.UNCONSCIOUS) || keys.contains(VitalService.DEAD)) {
            throw new InvalidCombatException(p.getName() + " está fora de combate e não pode agir");
        }
        if (keys.contains("PARALISADO")) throw new InvalidCombatException(p.getName() + " está Paralisado e não pode agir");
        boolean dissociated = keys.contains(VitalService.DISSOCIATED);
        if (dissociated && (action == CombatAction.MINOR || action == CombatAction.MINOR_OFFENSIVE)) {
            throw new InvalidCombatException("Dissociado: Ações Menores estão bloqueadas");
        }
        int pa = CombatRules.paCost(action, Boolean.TRUE.equals(req.slow()));
        if (action == CombatAction.CHANGE_GUARD && s.getDominance() == Dominance.AMBIDEXTROUS
                && p.getGuardChangesThisTurn() == 0) {
            pa = 0; // ambidestro: a 1ª troca de guarda do turno é Ação Livre
        }
        com.tatakai.manager.sheet.entity.InventoryItem item = gearItem(s, action, req.itemId());
        WeaponCatalog.Def weapon = item == null ? null : weapons.find(item.getWeaponKey()).orElse(null);
        BaseRules.Profile activeBase = profiles.baseProfile(s, p.getActiveBaseId());
        if (action == CombatAction.DRAW && activeBase != null && activeBase.readiness() && !p.isReadinessUsed()) {
            pa = 0; // Prontidão: sacar/guardar arma é Ação Livre uma vez por turno
            p.setReadinessUsed(true);
        }
        if (action == CombatAction.RELOAD) {
            pa = reloadCost(item, weapon);
            if (activeBase != null && activeBase.trainedReload() && !p.isTrainedReloadUsed() && weapon != null
                    && (weapon.shooter() || "Pólvora".equals(weapon.martialType()) || "Artesanais".equals(weapon.martialType()))) {
                pa = 0; // Recarga Treinada: Pólvora ou Artesanais têm 1 recarga por turno como Ação Livre
                p.setTrainedReloadUsed(true);
            }
        }
        ConsumableCatalog.Def consumable = action == CombatAction.USE_ITEM ? consumables.find(item.getConsumableKey())
                .orElseThrow(() -> new InvalidCombatException(item.getName() + " não é um consumível do catálogo")) : null;
        EncounterParticipant useTarget = consumable == null ? null
                : req.targetParticipantId() == null ? p : participantOf(e, req.targetParticipantId());
        if (consumable != null) pa = useCost(item, consumable, p, useTarget);
        if (item != null) gearCheck(s, action, item, weapon, req);
        if (action == CombatAction.CHANGE_BASE) {
            pa = CombatRules.baseChangeCost(p.getActiveBaseId() != null, req.baseId() != null);
            BaseRules.Profile target = req.baseId() == null ? null : profiles.baseProfile(s, req.baseId());
            if (target != null && target.fluidSwitch() && !p.isFluidSwitchUsed()) {
                pa = 0; // Troca Fluida: entrar nesta base é Ação Livre uma vez por turno
                p.setFluidSwitchUsed(true);
            }
        }
        // Ação Preparada com técnica de Preparação: o gasto é o SP da técnica (§38)
        RollProfileService.CoreTechnique prep = null;
        if (action == CombatAction.PREPARED && req.techniqueId() != null) {
            prep = profiles.coreTechniques(s, TechniqueCore.PREPARATION).stream()
                    .filter(x -> x.technique().getId().equals(req.techniqueId())
                            && java.util.Objects.equals(x.variation(), req.variation()))
                    .findFirst().orElseThrow(() -> new InvalidCombatException("Técnica de Preparação não encontrada ou inativa"));
        }
        int sp = VitalRules.adjustSp(prep != null ? prep.spCost() : CombatRules.spCost(action, req.spCost()),
                VitalRules.tier(p.getAdrenaline()), dissociated)
                + (pa >= 3 ? com.tatakai.manager.combat.service.rules.EquipmentRules.majorSp(armorWeight(s)) : 0); // Proteção Pesada
        if (sp > 0 && keys.contains(VitalService.EXHAUSTED)) {
            throw new InvalidCombatException("Exausto: sem técnicas nem ações com custo de SP");
        }
        if (action == CombatAction.BREATHE && VitalRules.collapse(s.getPhysicalFatigue())) {
            throw new InvalidCombatException("Colapso físico: não é possível recuperar SP");
        }
        if (p.getPaCurrent() < pa) {
            throw new InvalidCombatException("PA insuficiente: custa " + pa + " e restam " + p.getPaCurrent());
        }
        if (s.getCurrentSp() < sp) {
            throw new InvalidCombatException("SP insuficiente: custa " + sp + " e restam " + s.getCurrentSp());
        }
        List<CombatCondition> down = mine.stream().filter(c -> KNOCKED_DOWN.equals(c.getConditionKey())).toList();
        if (action == CombatAction.STAND_UP && down.isEmpty()) {
            throw new InvalidCombatException(p.getName() + " não está Derrubado");
        }
        EncounterParticipant stanchTarget = null;
        if (action == CombatAction.STANCH) {
            if (keys.contains(GRABBED) || keys.contains(RESTRAINED)) {
                throw new InvalidCombatException("Agarrado ou Imobilizado: não é possível estancar");
            }
            stanchTarget = req.targetParticipantId() == null ? p : participantOf(e, req.targetParticipantId());
            if (stanchTarget.getTeam() != p.getTeam()) {
                throw new InvalidCombatException("Só é possível estancar a si mesmo ou a um aliado");
            }
        }
        EncounterParticipant stabilizeTarget = null;
        if (action == CombatAction.STABILIZE) {
            if (req.targetParticipantId() == null) throw new InvalidCombatException("Escolha quem estabilizar");
            stabilizeTarget = participantOf(e, req.targetParticipantId());
            if (stabilizeTarget.getId().equals(p.getId()) || stabilizeTarget.getTeam() != p.getTeam()) {
                throw new InvalidCombatException("Só é possível estabilizar um aliado");
            }
            if (!vital.hasCondition(stabilizeTarget, VitalService.DYING)) {
                throw new InvalidCombatException(stabilizeTarget.getName() + " não está À Beira da Morte");
            }
        }
        if (action == CombatAction.EXTINGUISH && !keys.contains(VitalService.BURNING)) {
            throw new InvalidCombatException(p.getName() + " não está Queimando");
        }
        CharacterTechnique newBase = null;
        if (action == CombatAction.CHANGE_GUARD && req.guard() == null) {
            throw new InvalidCombatException("Informe a guarda");
        }
        if (action == CombatAction.CHANGE_BASE && req.baseId() != null) {
            if (keys.contains(BASE_BROKEN)) throw new InvalidCombatException("Quebra de Base: não é possível assumir uma base");
            newBase = martial.requireBase(s, req.baseId());
        }
        ActorRole actor = master ? ActorRole.MASTER : ActorRole.PLAYER;
        VitalService.Ctx ctx = VitalService.Ctx.of(userId, actor, "Combate: " + action.label());
        StringBuilder text = new StringBuilder(p.getName()).append(": ")
                .append(req.label() == null || req.label().isBlank() ? action.label() : req.label().trim());
        p.setPaCurrent(p.getPaCurrent() - pa);
        text.append(" (").append(pa).append(" PA");
        if (sp > 0) {
            vital.applySp(e, p, s, -sp, ctx);
            text.append(", ").append(sp).append(" SP");
        }
        text.append(")");
        switch (action) {
            case BREATHE -> {
                int before = s.getCurrentSp();
                int target = Math.min(s.getSpMax(), before + CombatRules.breatheRecovery(s.getCon()));
                vital.applySp(e, p, s, target - before, ctx);
                text.append(" — recuperou ").append(s.getCurrentSp() - before).append(" SP");
            }
            case MOVE -> {
                p.setMovesThisTurn(p.getMovesThisTurn() + 1);
                BaseRules.Profile base = vital.baseOf(p, s);
                double d = CombatRules.moveDistance(p.getMovesThisTurn(), s.getDex(), base == null ? 0 : base.moveMeters());
                if (keys.contains(VitalService.FREEZING)) d = d / 2; // Congelando: Movimentação pela metade
                if (SheetRules.overloaded(s)) d = d / 2;             // Sobrecarregado: idem
                if (keys.contains("ALEIJADO")) d = d / 2;            // Aleijar: Movimentação pela metade
                if (p.getMovesThisTurn() == 1) d = com.tatakai.manager.combat.service.rules.EquipmentRules.firstMove(d, armorWeight(s));
                text.append(" — Moveu ").append(d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d).replace('.', ',')).append(" m");
            }
            case STAND_UP -> {
                conditions.deleteAll(down);
                log(e, p.getId(), CombatEventType.CONDITION_REMOVED, p.getName() + " não está mais Derrubado");
            }
            case STANCH -> vital.stanch(e, p, stanchTarget, sheetOf(stanchTarget), ctx);
            case EXTINGUISH -> vital.extinguish(e, p, s, ctx);
            case PREPARED -> {
                if (prep != null) prepare(e, p, s, prep, ctx);
            }
            case STABILIZE -> vital.stabilize(e, p, s, stabilizeTarget, Boolean.TRUE.equals(req.medical()), ctx);
            case CHANGE_GUARD -> {
                p.setGuard(req.guard());
                p.setGuardChangedAt(java.time.Instant.now()); // reinicia o Timing
                p.setGuardChangesThisTurn(p.getGuardChangesThisTurn() + 1);
                text.append(" — guarda ").append(req.guard().label());
            }
            case CHANGE_BASE -> {
                p.setActiveBaseId(newBase == null ? null : newBase.getId());
                BaseRules.Profile entered = newBase == null ? null : profiles.baseProfile(s, newBase.getId());
                text.append(newBase == null ? " — sem base ativa" : " — base " + newBase.getName()
                        + (entered != null && entered.distanceControl() ? " (Controle de Distância)" : ""));
            }
            case DRAW, RELOAD, COCK, UNJAM, TOGGLE_MODE -> text.append(" — ").append(gear(action, item, weapon, req));
            case USE_ITEM -> text.append(" — ").append(useItem(e, p, s, item, consumable, useTarget, ctx, userId, actor));
            default -> { }
        }
        log(e, p.getId(), CombatEventType.ACTION, text.toString());
        return done(e, "ACTION", userId, master);
    }

    // ---------- consumíveis (§44) ----------

    /** Em si: Ação Menor (1 PA); em aliado: Ação Maior (3 PA); ofensivo: 2 PA; +1 PA se estiver no Acesso Rápido. */
    private static int useCost(com.tatakai.manager.sheet.entity.InventoryItem item, ConsumableCatalog.Def c, EncounterParticipant p,
                               EncounterParticipant target) {
        if ("NONE".equals(c.use())) throw new InvalidCombatException(c.name() + " não é usado em combate");
        if (item.getLocation() != ItemLocation.HAND && item.getLocation() != ItemLocation.QUICK) {
            throw new InvalidCombatException(item.getName() + " precisa estar em mãos ou no Acesso Rápido");
        }
        if (item.getQuantity() <= 0) throw new InvalidCombatException(item.getName() + " acabou");
        boolean other = !target.getId().equals(p.getId());
        if ("ALLY".equals(c.use()) && !other) throw new InvalidCombatException(c.name() + " é aplicado em outro lutador");
        if ("OFFENSIVE".equals(c.use()) && !other) throw new InvalidCombatException("Escolha o alvo");
        if (other && !"OFFENSIVE".equals(c.use()) && target.getTeam() != p.getTeam()) {
            throw new InvalidCombatException("Só é possível aplicar em um aliado");
        }
        int pa = "OFFENSIVE".equals(c.use()) ? 2 : other ? 3 : 1;
        return pa + (item.getLocation() == ItemLocation.QUICK ? 1 : 0); // pegar do Acesso Rápido: outra Ação Menor
    }

    private String useItem(Encounter e, EncounterParticipant p, CharacterSheet s, com.tatakai.manager.sheet.entity.InventoryItem item,
                           ConsumableCatalog.Def c, EncounterParticipant t, VitalService.Ctx ctx, UUID userId, ActorRole role) {
        CharacterSheet ts = t.getId().equals(p.getId()) ? s : sheetOf(t);
        String on = t.getId().equals(p.getId()) ? "" : " em " + t.getName();
        String out;
        switch (c.effect() == null ? "" : c.effect()) {
            case "STANCH" -> {
                vital.stanch(e, p, t, ts, ctx);
                out = c.name() + on + ": sangramento estancado";
            }
            case "STANCH_HEAL" -> {
                vital.stanch(e, p, t, ts, ctx);
                int hp = rolls.rollInternal(s, "1d4", c.name() + ": " + t.getName(), userId, role).getTotal();
                vital.applyHp(e, t, ts, hp, ctx);
                out = c.name() + on + ": estancado e +" + hp + " HP";
            }
            case "SP_1D6" -> {
                int sp = rolls.rollInternal(s, "1d6", c.name() + ": " + t.getName(), userId, role).getTotal();
                vital.applySp(e, t, ts, sp, ctx);
                out = c.name() + on + ": +" + sp + " SP";
            }
            case "WAKE_KP" -> {
                List<CombatCondition> tc = conditions.findByParticipantIdIn(List.of(t.getId()));
                CombatCondition ko = tc.stream().filter(x -> VitalService.UNCONSCIOUS.equals(x.getConditionKey())
                        && "PQ".equals(x.getNote())).findFirst()
                        .orElseThrow(() -> new InvalidCombatException(t.getName() + " não está Inconsciente por PQ"));
                if (tc.stream().anyMatch(x -> "AMONIA_USADA".equals(x.getConditionKey()))) {
                    throw new InvalidCombatException("Os Sais de Amônia já foram usados em " + t.getName() + " neste combate");
                }
                conditions.delete(ko);
                vital.onWake(e, t, ts, ko, ctx);
                vital.addCondition(e, t, "AMONIA_USADA", ConditionExpiry.END_OF_COMBAT, null, null);
                out = c.name() + on + ": acorda com 1 PQ, Desorientado";
            }
            case "GRIP_CHALK" -> {
                vital.addCondition(e, t, "GIZ_MAGNESIO", ConditionExpiry.COUNTER, 3, null);
                out = c.name() + on + ": +1 nas competições de Força do agarre por 3 rodadas";
            }
            case "STIMULANT" -> {
                conditions.findByParticipantIdIn(List.of(t.getId())).stream()
                        .filter(x -> VitalService.DISORIENTED.equals(x.getConditionKey()) || STUNNED.equals(x.getConditionKey()))
                        .toList().forEach(conditions::delete);
                vital.addCondition(e, t, VitalService.STIMULATED, ConditionExpiry.COUNTER, 3, null);
                out = c.name() + on + ": imune a Desorientado/Atordoado e +2 em Vontade por 3 rodadas";
            }
            case "PEPPER" -> {
                boolean hit = contest(e, p, s, Attribute.DEX, 0, t, ts, Attribute.CON, 0, c.name(), userId, role);
                if (hit) {
                    int rounds = rolls.rollInternal(s, "1d4", c.name() + ": duração", userId, role).getTotal();
                    vital.addCondition(e, t, "OLHO_IRRITADO", ConditionExpiry.COUNTER, rounds, "só sai com água");
                }
                out = c.name() + on + (hit ? ": Olho Irritado" : ": errou");
            }
            default -> out = c.name() + on + ": aplicar o efeito manualmente";
        }
        item.setQuantity(item.getQuantity() - 1);
        if (item.getQuantity() <= 0) s.getInventory().remove(item);
        return out;
    }

    /** Categoria de peso mais pesada vestida (null sem proteção, §43). */
    private String armorWeight(CharacterSheet s) {
        return com.tatakai.manager.combat.service.rules.EquipmentRules.heaviest(equipment.worn(s).stream()
                .map(EquipmentCatalog.Def::category).filter(java.util.Objects::nonNull).toList());
    }

    // ---------- armas em mãos (§42) ----------

    /** Item exigido pelas ações de equipamento (null para as demais ações). */
    private static com.tatakai.manager.sheet.entity.InventoryItem gearItem(CharacterSheet s, CombatAction a, UUID itemId) {
        if (a != CombatAction.DRAW && a != CombatAction.RELOAD && a != CombatAction.COCK && a != CombatAction.UNJAM
                && a != CombatAction.TOGGLE_MODE && a != CombatAction.USE_ITEM) {
            return null;
        }
        if (itemId == null) throw new InvalidCombatException("Escolha o item");
        return s.getInventory().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new InvalidCombatException("Item não encontrado no inventário"));
    }

    /** Recarga: sem propriedade 1 PA (Ação Menor); Recarga Lenta 3 PA (Ação Maior); Rápida não precisa. */
    private static int reloadCost(com.tatakai.manager.sheet.entity.InventoryItem item, WeaponCatalog.Def w) {
        if (w == null || w.capacity() == null) throw new InvalidCombatException(item.getName() + " não usa munição");
        if ("FAST".equals(w.reload())) throw new InvalidCombatException("Recarga Rápida: a recarga já faz parte do ataque");
        return "SLOW".equals(w.reload()) ? 3 : 1;
    }

    /** Mãos que o item ocupa: Pesada, Grande, disparo/fogo não Compacta e Empunhadura Variável com as duas = 2. */
    private static int hands(com.tatakai.manager.sheet.entity.InventoryItem i, WeaponCatalog.Def w) {
        return w != null && (w.forcesTwoHands() || (w.variable() && i.isTwoHanded())) ? 2 : 1;
    }

    /** Validações das ações de equipamento, antes de gastar PA. */
    private void gearCheck(CharacterSheet s, CombatAction a, com.tatakai.manager.sheet.entity.InventoryItem item,
                           WeaponCatalog.Def w, CombatActionRequest req) {
        boolean inHand = item.getLocation() == ItemLocation.HAND;
        switch (a) {
            case DRAW -> {
                if (inHand) return;
                int used = s.getInventory().stream().filter(i -> i != item && i.getLocation() == ItemLocation.HAND)
                        .mapToInt(i -> hands(i, weapons.find(i.getWeaponKey()).orElse(null))).sum();
                boolean two = w != null && (w.forcesTwoHands() || (w.variable() && Boolean.TRUE.equals(req.twoHanded())));
                if (used + (two ? 2 : 1) > 2) throw new InvalidCombatException("Mãos ocupadas: guarde algo antes");
            }
            case RELOAD -> {
                if (!inHand) throw new InvalidCombatException("A arma precisa estar em mãos");
            }
            case COCK -> {
                if (w == null || !w.slowCock()) throw new InvalidCombatException(item.getName() + " não precisa ser engatilhada");
                if (!inHand) throw new InvalidCombatException("A arma precisa estar em mãos");
            }
            case UNJAM -> {
                if (!item.isJammed()) throw new InvalidCombatException(item.getName() + " não está travada");
            }
            case TOGGLE_MODE -> {
                if (w == null || w.alt() == null) throw new InvalidCombatException(item.getName() + " não tem outro modo");
            }
            default -> { }
        }
    }

    /** Aplica a ação de equipamento já validada; devolve o texto para a linha do tempo. */
    private String gear(CombatAction a, com.tatakai.manager.sheet.entity.InventoryItem item, WeaponCatalog.Def w,
                        CombatActionRequest req) {
        switch (a) {
            case DRAW -> {
                if (item.getLocation() == ItemLocation.HAND) {
                    item.setLocation(ItemLocation.QUICK);
                    return "guardou " + item.getName();
                }
                item.setTwoHanded(w != null && w.variable() && Boolean.TRUE.equals(req.twoHanded()));
                String from = item.getLocation() == ItemLocation.DROPPED ? "pegou do chão " : "sacou ";
                item.setLocation(ItemLocation.HAND);
                return from + item.getName() + (item.isTwoHanded() ? " (duas mãos)" : "");
            }
            case RELOAD -> {
                item.setLoaded(w.capacity());
                return "recarregou " + item.getName() + " (" + w.capacity() + ")";
            }
            case COCK -> {
                item.setCocked(true);
                return "engatilhou " + item.getName();
            }
            case UNJAM -> {
                item.setJammed(false);
                return "destravou " + item.getName();
            }
            case TOGGLE_MODE -> {
                item.setWeaponKey(w.alt());
                return item.getName() + " no modo " + weapons.find(w.alt()).map(WeaponCatalog.Def::name).orElse(w.alt());
            }
            default -> throw new IllegalStateException();
        }
    }

    /**
     * Técnica de Preparação (§38): condição PREPARADO até o início do próximo turno (com Sustentação, até o fim dele),
     * com a técnica e o que ainda pode ser consumido na nota ("id;variação;flags": L = Leitura, C = Carga).
     * Fôlego Tático recupera SP na hora.
     */
    private void prepare(Encounter e, EncounterParticipant p, CharacterSheet s, RollProfileService.CoreTechnique t,
                         VitalService.Ctx ctx) {
        TechniqueRules.Preparation pr = TechniqueRules.preparation(t.items());
        String flags = (pr.reading() ? "L" : "") + (pr.charge() > 0 ? "C" : "");
        conditions.findByParticipantIdIn(List.of(p.getId())).stream()
                .filter(c -> "PREPARADO".equals(c.getConditionKey())).forEach(conditions::delete);
        conditions.save(CombatCondition.builder().participantId(p.getId()).conditionKey("PREPARADO")
                .expiresAt(pr.sustain() ? ConditionExpiry.COUNTER : ConditionExpiry.START_OF_TARGET_TURN)
                .turnsRemaining(pr.sustain() ? 2 : null).appliedTurn(e.getTurnCounter()) // 2: o contador desconta já no fim deste turno
                .note(t.technique().getId() + ";" + (t.variation() == null ? "" : t.variation()) + ";" + flags).build());
        log(e, p.getId(), CombatEventType.CONDITION_ADDED, p.getName() + " se prepara com " + t.name());
        if (pr.breath()) vital.applySp(e, p, s, Math.max(0, RollRules.modOrZero(s.getCon())), ctx);
    }

    // ---------- §39: agarre (Ataques Básicos) ----------

    /**
     * Ações de agarre no turno do participante. Fuga: Agarrado/Clinch/Estrangulado com Ação Menor (1 PA), Imobilizado
     * com Ação Maior (3 PA), competição contra quem agarra. Projeção e Derrubar pela perna (Ação Maior + 1 SP, FOR contra
     * FOR ou DES), Constrição (Ação Maior + 1 SP, FOR × FOR → Imobilizado), Estrangular (Ação Maior), Manter (Ação Menor
     * ofensiva, 2 PA + 1 SP; Constrição pode torcer 1d2 + FOR), Soltar (livre). Tração da base soma nas competições.
     */
    public EncounterResponse grapple(UUID encounterId, GrappleRequest req, UUID userId) {
        Encounter e = load(encounterId);
        boolean master = requireMembership(e.getCampaignId(), userId) == Role.MASTER;
        requireActive(e);
        EncounterParticipant p = participantOf(e, req.participantId());
        CharacterSheet s = sheetOf(p);
        if (!master && !userId.equals(ownerUserId(s))) {
            throw new AccessDeniedException("Você só pode agir pelo seu próprio personagem");
        }
        List<EncounterParticipant> ps = participants.findByEncounterIdOrderByOrderIndex(e.getId());
        if (!ps.get(e.getCurrentIndex()).getId().equals(p.getId())) {
            throw new InvalidCombatException("Não é o turno de " + p.getName());
        }
        java.util.Set<String> keys = conditions.findByParticipantIdIn(List.of(p.getId())).stream()
                .map(CombatCondition::getConditionKey).collect(Collectors.toSet());
        if (keys.contains(VitalService.UNCONSCIOUS) || keys.contains(VitalService.DEAD) || keys.contains("PARALISADO")) {
            throw new InvalidCombatException(p.getName() + " não pode agir");
        }
        ActorRole role = master ? ActorRole.MASTER : ActorRole.PLAYER;
        VitalService.Ctx ctx = VitalService.Ctx.of(userId, role, "Agarre");
        CombatCondition tangle = conditions.findByParticipantIdIn(List.of(p.getId())).stream()
                .filter(c -> GrappleRules.GRABBED.equals(c.getConditionKey()) && AttackService.ENTANGLED.equals(c.getNote()))
                .findFirst().orElse(null);
        if (req.action() == GrappleRequest.Action.UNTANGLE
                || (req.action() == GrappleRequest.Action.ESCAPE && tangle != null && !heldBySomeone(p))) {
            untangle(e, p, s, tangle, req.action() == GrappleRequest.Action.UNTANGLE ? null
                    : req.attribute() == null ? Attribute.STR : req.attribute(), ctx);
            return done(e, "GRAPPLE", userId, master);
        }
        if (req.action() == GrappleRequest.Action.ESCAPE) {
            escape(e, p, s, req.attribute() == null ? Attribute.STR : req.attribute(), ctx, userId, role);
            return done(e, "GRAPPLE", userId, master);
        }
        if (req.targetParticipantId() == null) throw new InvalidCombatException("Escolha o alvo do agarre");
        EncounterParticipant t = participantOf(e, req.targetParticipantId());
        CharacterSheet ts = sheetOf(t);
        CombatCondition grab = hold(t, GrappleRules.GRABBED, p);
        CombatCondition clinchInit = hold(t, GrappleRules.CLINCH, p);
        boolean initiated = clinchInit != null && GrappleRules.CLINCH_INIT.equals(clinchInit.getNote());
        switch (req.action()) {
            case PROJECT -> {
                if (!initiated && !(grab != null && GrappleRules.Grip.TORSO.name().equals(grab.getNote()))) {
                    throw new InvalidCombatException("Projeção exige agarre no tronco ou um Clinch que você iniciou");
                }
                spend(e, p, s, 3, 1, "Projeção", ctx);
                Attribute def = RollRules.modOrZero(ts.getDex()) > RollRules.modOrZero(ts.getStr()) ? Attribute.DEX : Attribute.STR;
                if (contest(e, p, s, Attribute.STR, 0, t, ts, def, 0, "Projeção", userId, role)) {
                    releaseBetween(e, p, t);
                    takedown(e, p, t, ts, "Projeção", ctx);
                }
            }
            case LEG_TAKEDOWN -> {
                if (grab == null || !GrappleRules.Grip.LEG.name().equals(grab.getNote())) {
                    throw new InvalidCombatException("Derrubar pela perna exige agarre na perna");
                }
                spend(e, p, s, 3, 1, "Derrubar pela perna", ctx);
                if (contest(e, p, s, Attribute.STR, 0, t, ts, Attribute.STR, 0, "Derrubar pela perna", userId, role)) {
                    takedown(e, p, t, ts, "Derrubar pela perna", ctx);
                }
            }
            case CONSTRICT -> {
                boolean downed = conditions.findByParticipantIdIn(List.of(t.getId())).stream()
                        .anyMatch(c -> KNOCKED_DOWN.equals(c.getConditionKey()));
                if (grab == null || !(GrappleRules.Grip.ARM.name().equals(grab.getNote()) || downed)) {
                    throw new InvalidCombatException("Constrição exige agarre no braço, ou alvo Derrubado já agarrado");
                }
                spend(e, p, s, 3, 1, "Constrição", ctx);
                if (contest(e, p, s, Attribute.STR, 0, t, ts, Attribute.STR, 0, "Constrição", userId, role)) {
                    conditions.delete(grab);
                    holdOn(e, t, GrappleRules.RESTRAINED, p, GrappleRules.CONSTRICTION);
                }
            }
            case STRANGLE -> {
                if (!initiated && !(grab != null && GrappleRules.Grip.NECK.name().equals(grab.getNote()))) {
                    throw new InvalidCombatException("Estrangular exige agarre no pescoço ou um Clinch que você iniciou");
                }
                if (req.strangleType() == null) throw new InvalidCombatException("Escolha sanguíneo ou respiratório");
                spend(e, p, s, 3, 0, "Estrangulamento", ctx);
                holdOn(e, t, GrappleRules.STRANGLED, p, req.strangleType().name());
            }
            case MAINTAIN -> {
                List<CombatCondition> kept = conditions.findByParticipantIdIn(List.of(t.getId())).stream()
                        .filter(c -> p.getId().equals(c.getSourceParticipantId()) && (GrappleRules.STRANGLED.equals(c.getConditionKey())
                                || (GrappleRules.RESTRAINED.equals(c.getConditionKey()) && GrappleRules.CONSTRICTION.equals(c.getNote()))))
                        .toList();
                if (kept.isEmpty()) throw new InvalidCombatException("Nada a manter: sem estrangulamento ou constrição");
                spend(e, p, s, 2, 1, "Manter o agarre", ctx);
                kept.forEach(c -> { c.setAppliedTurn(e.getTurnCounter()); conditions.save(c); });
                log(e, p.getId(), CombatEventType.ACTION, p.getName() + " mantém o agarre em " + t.getName());
                if (Boolean.TRUE.equals(req.twist()) && kept.stream().anyMatch(c -> GrappleRules.RESTRAINED.equals(c.getConditionKey()))) {
                    int d = Math.max(0, rolls.rollInternal(s, "1d2", "Torcer: " + p.getName() + " → " + t.getName(), userId, role)
                            .getTotal() + RollRules.modOrZero(s.getStr()));
                    log(e, t.getId(), CombatEventType.ACTION, p.getName() + " torce: " + d + " de dano em " + t.getName());
                    vital.applyHp(e, t, ts, -d, ctx);
                }
            }
            case RELEASE -> {
                releaseBetween(e, p, t);
                log(e, p.getId(), CombatEventType.ACTION, p.getName() + " solta " + t.getName());
            }
            default -> { }
        }
        return done(e, "GRAPPLE", userId, master);
    }

    private boolean heldBySomeone(EncounterParticipant p) {
        return conditions.findByParticipantIdIn(List.of(p.getId())).stream()
                .anyMatch(c -> GrappleRules.HOLDS.contains(c.getConditionKey()) && c.getSourceParticipantId() != null);
    }

    /** Enredado (§42): sem teste com Ação Maior (3 PA), ou Ação Menor (1 PA) + FOR/DES contra ND 14. */
    private void untangle(Encounter e, EncounterParticipant p, CharacterSheet s, CombatCondition tangle, Attribute attr,
                          VitalService.Ctx ctx) {
        if (tangle == null) throw new InvalidCombatException(p.getName() + " não está enredado");
        if (attr != null && attr != Attribute.STR && attr != Attribute.DEX) throw new InvalidCombatException("Use Força ou Destreza");
        int pa = attr == null ? 3 : 1;
        if (p.getPaCurrent() < pa) throw new InvalidCombatException("PA insuficiente: custa " + pa + " e restam " + p.getPaCurrent());
        p.setPaCurrent(p.getPaCurrent() - pa);
        if (attr == null || vital.attributeTest(e, p, s, attr, com.tatakai.manager.combat.service.rules.WeaponRules.ENTANGLE_ND,
                "Soltar-se da arma", vital.conditionAdvantage(p, s, attr), ctx)) {
            conditions.delete(tangle);
            log(e, p.getId(), CombatEventType.ACTION, p.getName() + " se solta (" + pa + " PA)");
        }
    }

    /** Fuga: a condição mais forte que prende o participante decide custo e desvantagem; vencer solta tudo daquele par. */
    private void escape(Encounter e, EncounterParticipant p, CharacterSheet s, Attribute attr, VitalService.Ctx ctx,
                        UUID userId, ActorRole role) {
        List<CombatCondition> mine = conditions.findByParticipantIdIn(List.of(p.getId()));
        CombatCondition hold = null;
        for (String key : List.of(GrappleRules.RESTRAINED, GrappleRules.STRANGLED, GrappleRules.GRABBED, GrappleRules.CLINCH)) {
            hold = mine.stream().filter(c -> key.equals(c.getConditionKey()) && c.getSourceParticipantId() != null)
                    .findFirst().orElse(null);
            if (hold != null) break;
        }
        if (hold == null) throw new InvalidCombatException(p.getName() + " não está preso");
        if (!GrappleRules.escapeAllows(hold.getConditionKey(), attr, martial.fighter(p, s).type())) {
            throw new InvalidCombatException("Não é possível escapar com esse atributo");
        }
        EncounterParticipant g = participantOf(e, hold.getSourceParticipantId());
        spend(e, p, s, GrappleRules.escapeCost(hold.getConditionKey()), 0, "Escapar", ctx);
        int adv = (GrappleRules.escapeDisadvantage(hold.getConditionKey(), hold.getNote()) ? -1 : 0)
                - (mine.stream().anyMatch(c -> GrappleRules.PANIC.equals(c.getConditionKey())) ? 1 : 0);
        Attribute gAttr = GrappleRules.RESTRAINED.equals(hold.getConditionKey()) ? attr : Attribute.STR;
        int gi = equipment.wears(s, "KIMONO") ? 2 : 0; // Kimono: as pegadas estão prontas para quem agarra
        int mine0 = attr == Attribute.STR && vital.hasCondition(p, "GIZ_MAGNESIO") ? 1 : 0;   // Giz de Magnésio
        int theirs = gAttr == Attribute.STR && vital.hasCondition(g, "GIZ_MAGNESIO") ? 1 : 0;
        if (contest(e, p, s, attr, adv, mine0, g, sheetOf(g), gAttr, 0, gi + theirs, "Escapar", userId, role)) {
            releaseBetween(e, g, p);
            log(e, p.getId(), CombatEventType.ACTION, p.getName() + " escapa de " + g.getName());
        }
    }

    /** Competição de atributos (com Tração da base de cada um); true = o primeiro vence (empate fica com quem resiste). */
    private boolean contest(Encounter e, EncounterParticipant a, CharacterSheet as, Attribute aAttr, int aAdv,
                            EncounterParticipant b, CharacterSheet bs, Attribute bAttr, int bAdv, String label,
                            UUID userId, ActorRole role) {
        return contest(e, a, as, aAttr, aAdv, 0, b, bs, bAttr, bAdv, 0, label, userId, role);
    }

    /** Competição com bônus fixos de equipamento (Kimono, Giz de Magnésio). */
    private boolean contest(Encounter e, EncounterParticipant a, CharacterSheet as, Attribute aAttr, int aAdv, int aBonus,
                            EncounterParticipant b, CharacterSheet bs, Attribute bAttr, int bAdv, int bBonus,
                            String label, UUID userId, ActorRole role) {
        int ta = contestRoll(e, a, as, aAttr, aAdv, aBonus, label, userId, role);
        int tb = contestRoll(e, b, bs, bAttr, bAdv, bBonus, label, userId, role);
        boolean won = GrappleRules.wins(ta, tb);
        log(e, a.getId(), CombatEventType.ACTION, label + ": " + a.getName() + " " + ta + " × " + tb + " " + b.getName()
                + " — " + (won ? "sucesso" : "falha"));
        return won;
    }

    private int contestRoll(Encounter e, EncounterParticipant p, CharacterSheet s, Attribute attr, int adv, int bonus,
                            String label, UUID userId, ActorRole role) {
        RollProfileService.Calc c = profiles.attribute(s, attr);
        List<RollPart> hit = new ArrayList<>(c.hit());
        if (bonus != 0) hit.add(new RollPart("HIT", "Equipamento", bonus, null, false));
        BaseRules.Profile base = profiles.baseProfile(s, p.getActiveBaseId());
        if (base != null && base.traction() != 0) {
            hit.add(new RollPart("HIT", "Tração (base: " + base.name() + ")", base.traction(), null, false));
        }
        c = c.with(c.spCost(), c.critMargin(), c.advantage(), hit, c.damage());
        return rolls.rollCalc(s, e.getCampaignId(), c, adv + vital.conditionAdvantage(p, s, attr),
                label + ": " + p.getName(), userId, role).total();
    }

    private void spend(Encounter e, EncounterParticipant p, CharacterSheet s, int pa, int sp, String label, VitalService.Ctx ctx) {
        if (p.getPaCurrent() < pa) throw new InvalidCombatException("PA insuficiente: " + label + " custa " + pa);
        if (sp > 0 && conditions.findByParticipantIdIn(List.of(p.getId())).stream()
                .anyMatch(c -> VitalService.EXHAUSTED.equals(c.getConditionKey()))) {
            throw new InvalidCombatException("Exausto: sem ações com custo de SP");
        }
        if (s.getCurrentSp() < sp) throw new InvalidCombatException("SP insuficiente: " + label + " custa " + sp);
        p.setPaCurrent(p.getPaCurrent() - pa);
        if (sp > 0) vital.applySp(e, p, s, -sp, ctx);
        log(e, p.getId(), CombatEventType.ACTION, p.getName() + ": " + label + " (" + pa + " PA" + (sp > 0 ? ", " + sp + " SP" : "") + ")");
    }

    /** Condição de agarre que `source` mantém sobre `t` (null = nenhuma). */
    private CombatCondition hold(EncounterParticipant t, String key, EncounterParticipant source) {
        return conditions.findByParticipantIdIn(List.of(t.getId())).stream()
                .filter(c -> key.equals(c.getConditionKey()) && source.getId().equals(c.getSourceParticipantId()))
                .findFirst().orElse(null);
    }

    /** Aplica (ou renova) uma condição de agarre com origem e nota, marcada como mantida neste turno. */
    public void holdOn(Encounter e, EncounterParticipant t, String key, EncounterParticipant source, String note) {
        CombatCondition c = hold(t, key, source);
        if (c == null) c = CombatCondition.builder().participantId(t.getId()).conditionKey(key).build();
        c.setSourceParticipantId(source.getId());
        c.setNote(note);
        c.setExpiresAt(ConditionExpiry.MANUAL);
        c.setAppliedTurn(e.getTurnCounter());
        conditions.save(c);
        log(e, t.getId(), CombatEventType.CONDITION_ADDED, t.getName() + ": "
                + catalog.find(key).map(ConditionCatalog.Def::name).orElse(key) + " por " + source.getName()
                + (note == null ? "" : " (" + note + ")"));
        onConditionApplied(e, t, key);
    }

    /** Desfaz todo agarre entre os dois (nos dois sentidos); desmaio por estrangulamento passa a durar mais 1 rodada. */
    private void releaseBetween(Encounter e, EncounterParticipant a, EncounterParticipant b) {
        for (EncounterParticipant[] pair : List.of(new EncounterParticipant[]{a, b}, new EncounterParticipant[]{b, a})) {
            for (CombatCondition c : conditions.findByParticipantIdIn(List.of(pair[1].getId()))) {
                if (GrappleRules.HOLDS.contains(c.getConditionKey()) && pair[0].getId().equals(c.getSourceParticipantId())) {
                    conditions.delete(c);
                }
                if (VitalService.UNCONSCIOUS.equals(c.getConditionKey()) && "ESTRANGULAMENTO".equals(c.getNote())
                        && c.getExpiresAt() == ConditionExpiry.MANUAL) {
                    c.setExpiresAt(ConditionExpiry.COUNTER);
                    c.setTurnsRemaining(1);
                    conditions.save(c);
                }
            }
        }
    }

    private void takedown(Encounter e, EncounterParticipant by, EncounterParticipant t, CharacterSheet ts, String label,
                          VitalService.Ctx ctx) {
        vital.addEffectCondition(e, t, KNOCKED_DOWN, null, label, by.getId());
        onConditionApplied(e, t, KNOCKED_DOWN);
        int dmg = EffectRules.takedownDamage(ts.getWeightKg());
        log(e, t.getId(), CombatEventType.ACTION, t.getName() + " sofre " + dmg + " de dano de Derrubada");
        vital.applyHp(e, t, ts, -dmg, ctx);
    }

    /** Fim do turno de quem agarra: estrangulamento e constrição não mantidos neste turno se desfazem. */
    private void dropUnmaintained(Encounter e, EncounterParticipant p) {
        int started = p.getTurnStartedAt() == null ? e.getTurnCounter() : p.getTurnStartedAt();
        List<UUID> ids = participants.findByEncounterIdOrderByOrderIndex(e.getId()).stream().map(EncounterParticipant::getId).toList();
        for (CombatCondition c : conditions.findByParticipantIdIn(ids)) {
            boolean kept = GrappleRules.STRANGLED.equals(c.getConditionKey())
                    || (GrappleRules.RESTRAINED.equals(c.getConditionKey()) && GrappleRules.CONSTRICTION.equals(c.getNote()));
            if (kept && p.getId().equals(c.getSourceParticipantId()) && c.getAppliedTurn() < started) {
                EncounterParticipant victim = participantOf(e, c.getParticipantId());
                conditions.delete(c);
                log(e, victim.getId(), CombatEventType.CONDITION_REMOVED, p.getName() + " não manteve o agarre: "
                        + victim.getName() + " está livre de " + catalog.find(c.getConditionKey()).map(ConditionCatalog.Def::name).orElse(c.getConditionKey()));
                if (GrappleRules.STRANGLED.equals(c.getConditionKey())) releaseBetween(e, p, victim);
            }
        }
    }

    public EncounterResponse nextTurn(UUID encounterId, UUID userId) {
        Encounter e = load(encounterId);
        Role role = requireMembership(e.getCampaignId(), userId);
        boolean master = role == Role.MASTER;
        requireActive(e);
        List<EncounterParticipant> ps = participants.findByEncounterIdOrderByOrderIndex(e.getId());
        EncounterParticipant current = ps.get(e.getCurrentIndex());
        if (!master && !userId.equals(ownerUserId(sheetOf(current)))) {
            throw new AccessDeniedException("Apenas o Mestre ou o dono do turno pode avançá-lo");
        }
        if (attacks.existsByEncounterIdAndStatus(e.getId(), AttackStatus.AWAITING_REACTIONS)) {
            throw new CombatConflictException("Há ataque aguardando reação");
        }
        VitalService.Ctx ctx = VitalService.Ctx.of(userId, master ? ActorRole.MASTER : ActorRole.PLAYER, "Combate");
        endTurn(e, current, ctx);
        int next = e.getCurrentIndex() + 1;
        if (next >= ps.size()) {
            next = 0;
            endRound(e, ps);
        }
        e.setCurrentIndex(next);
        beginTurn(e, ps.get(next), ps, ctx);
        return done(e, "NEXT_TURN", userId, master);
    }

    private void expire(Encounter e, EncounterParticipant p, CombatCondition c, VitalService.Ctx ctx) {
        conditions.delete(c);
        String name = catalog.find(c.getConditionKey()).map(ConditionCatalog.Def::name).orElse(c.getConditionKey());
        log(e, p.getId(), CombatEventType.CONDITION_EXPIRED, p.getName() + ": " + name + " terminou");
        if (ctx != null && VitalService.UNCONSCIOUS.equals(c.getConditionKey())) {
            vital.onWake(e, p, sheetOf(p), c, ctx);
        }
        if (ctx != null && VitalService.STIMULATED.equals(c.getConditionKey())) vital.stimulantCrash(e, p, sheetOf(p), ctx);
    }

    private void endTurn(Encounter e, EncounterParticipant p, VitalService.Ctx ctx) {
        int started = p.getTurnStartedAt() == null ? e.getTurnCounter() : p.getTurnStartedAt();
        for (CombatCondition c : new ArrayList<>(conditions.findByParticipantIdIn(List.of(p.getId())))) {
            if (CombatRules.expiresAtTurnEnd(c.getExpiresAt(), c.getAppliedTurn(), started)) {
                expire(e, p, c, ctx);
            } else if (c.getExpiresAt() == ConditionExpiry.COUNTER) {
                int left = CombatRules.tickCounter(c.getTurnsRemaining());
                if (left <= 0) expire(e, p, c, ctx);
                else c.setTurnsRemaining(left);
            }
        }
        vital.onTurnEnd(e, p, sheetOf(p), ctx);
        dropUnmaintained(e, p);
        p.setPaCurrent(0);
        log(e, p.getId(), CombatEventType.TURN_END, "Fim do turno de " + p.getName());
    }

    private void endRound(Encounter e, List<EncounterParticipant> ps) {
        Map<UUID, EncounterParticipant> byId = ps.stream().collect(Collectors.toMap(EncounterParticipant::getId, Function.identity()));
        for (CombatCondition c : new ArrayList<>(conditions.findByParticipantIdIn(byId.keySet()))) {
            if (CombatRules.expiresAtRoundEnd(c.getExpiresAt())) expire(e, byId.get(c.getParticipantId()), c, null);
        }
        vital.onRoundEnd(e, ps);
        log(e, null, CombatEventType.ROUND_END, "Fim da rodada " + e.getRound());
        e.setRound(e.getRound() + 1);
    }

    private void beginTurn(Encounter e, EncounterParticipant p, List<EncounterParticipant> all, VitalService.Ctx ctx) {
        e.setTurnCounter(e.getTurnCounter() + 1);
        p.setTurnStartedAt(e.getTurnCounter());
        for (EncounterParticipant x : all) x.setDamageThisTurn(0);
        List<CombatCondition> mine = new ArrayList<>(conditions.findByParticipantIdIn(List.of(p.getId())));
        for (CombatCondition c : new ArrayList<>(mine)) {
            if (CombatRules.expiresAtTurnStart(c.getExpiresAt())) {
                expire(e, p, c, ctx);
                mine.remove(c);
            }
        }
        p.setPaCurrent(CombatRules.rechargePa(p.getPaBonusNext(), p.getPaLossNext()));
        p.setPaBonusNext(0);
        p.setPaLossNext(0);
        p.setMovesThisTurn(0);
        p.setGuardChangesThisTurn(0);
        p.setFluidSwitchUsed(false);
        p.setReadinessUsed(false);
        p.setTrainedReloadUsed(false);
        p.setAttackersThisRound(new ArrayList<>());
        vital.onTurnStart(e, p, sheetOf(p), ctx);
        if (VitalRules.activeHysteria(p.getAdrenaline(), p.getHysteria()) == Hysteria.LIGHTNING) {
            p.setPaCurrent(p.getPaCurrent() + 1);
        }
        log(e, p.getId(), CombatEventType.TURN_START,
                "Rodada " + e.getRound() + ": turno de " + p.getName() + " (" + p.getPaCurrent() + " PA)");
        mine = new ArrayList<>(conditions.findByParticipantIdIn(List.of(p.getId())));
        if (mine.stream().anyMatch(c -> STUNNED.equals(c.getConditionKey()))) {
            p.setPaCurrent(0);
            log(e, p.getId(), CombatEventType.TURN_LOST, p.getName() + " está Atordoado e perde o turno");
        } else if (mine.stream().anyMatch(c -> VitalService.UNCONSCIOUS.equals(c.getConditionKey())
                || VitalService.DEAD.equals(c.getConditionKey()))) {
            p.setPaCurrent(0);
            log(e, p.getId(), CombatEventType.TURN_LOST, p.getName() + " está fora de combate e perde o turno");
        }
    }

    // ---------- F6c: Histeria e ajuste manual de recursos ----------

    public EncounterResponse setHysteria(UUID encounterId, UUID participantId, SetHysteriaRequest req, UUID userId) {
        Encounter e = load(encounterId);
        boolean master = requireMembership(e.getCampaignId(), userId) == Role.MASTER;
        requireActive(e);
        EncounterParticipant p = participantOf(e, participantId);
        if (!master && !userId.equals(ownerUserId(sheetOf(p)))) {
            throw new AccessDeniedException("Você só pode agir pelo seu próprio personagem");
        }
        vital.setHysteria(e, p, req.hysteria());
        return done(e, "HYSTERIA", userId, master);
    }

    public EncounterResponse adjustVital(UUID encounterId, UUID participantId, VitalAdjustRequest req, UUID userId) {
        Encounter e = loadAsMaster(encounterId, userId);
        requireNotEnded(e);
        EncounterParticipant p = participantOf(e, participantId);
        CharacterSheet s = sheetOf(p);
        VitalService.Ctx ctx = VitalService.Ctx.of(userId, ActorRole.MASTER, "Mestre: " + req.reason());
        log(e, p.getId(), CombatEventType.GM, "Mestre ajustou " + req.resource() + " de " + p.getName() + " em "
                + req.delta() + " (" + req.reason() + ")");
        switch (req.resource()) {
            case HP -> vital.applyHp(e, p, s, req.delta(), ctx);
            case SP -> vital.applySp(e, p, s, req.delta(), ctx);
            case KP -> vital.applyKp(e, p, s, req.delta(), ctx);
        }
        return done(e, "VITALS", userId, true);
    }

    // ---------- condições ----------

    public EncounterResponse addCondition(UUID encounterId, UUID participantId, AddConditionRequest req, UUID userId) {
        Encounter e = loadAsMaster(encounterId, userId);
        requireNotEnded(e);
        EncounterParticipant p = participantOf(e, participantId);
        ConditionCatalog.Def def = catalog.find(req.key())
                .orElseThrow(() -> new InvalidCombatException("Condição desconhecida: " + req.key()));
        if (req.sourceParticipantId() != null) participantOf(e, req.sourceParticipantId());
        ConditionExpiry expiry = req.expiresAt() != null ? req.expiresAt() : def.defaultExpiresAt();
        if (expiry == ConditionExpiry.COUNTER && req.turnsRemaining() == null) {
            throw new InvalidCombatException("Informe a quantidade de turnos do contador");
        }
        Integer turns = expiry == ConditionExpiry.COUNTER ? req.turnsRemaining() : null;
        CombatCondition existing = conditions.findByParticipantIdIn(List.of(p.getId())).stream()
                .filter(c -> c.getConditionKey().equals(def.key())).findFirst().orElse(null);
        CombatCondition c = existing != null ? existing
                : CombatCondition.builder().participantId(p.getId()).conditionKey(def.key()).build();
        // não acumula: com níveis vale o mais forte; sem níveis, só renova
        Integer level = req.intensity();
        if (def.leveled()) {
            level = Math.max(level == null ? 1 : level,
                    existing == null || existing.getIntensity() == null ? 0 : existing.getIntensity());
        }
        c.setIntensity(level);
        c.setSourceParticipantId(req.sourceParticipantId());
        c.setExpiresAt(expiry);
        c.setAppliedTurn(e.getTurnCounter());
        c.setTurnsRemaining(turns);
        c.setNote(req.note());
        conditions.save(c);
        log(e, p.getId(), CombatEventType.CONDITION_ADDED, p.getName() + ": " + def.name()
                + (def.leveled() ? " (" + def.levels().get(Math.min(def.levels().size(), level) - 1) + ")" : "")
                + (existing == null ? " aplicada" : " renovada"));
        onConditionApplied(e, p, def.key());
        return done(e, "CONDITION", userId, true);
    }

    /** Derrubado e Quebra de Base derrubam a base ativa; Derrubado/Agarrado/Imobilizado zeram contadores (F6d). */
    private void onConditionApplied(Encounter e, EncounterParticipant p, String key) {
        boolean down = KNOCKED_DOWN.equals(key);
        if ((down || BASE_BROKEN.equals(key)) && p.getActiveBaseId() != null) {
            p.setActiveBaseId(null);
            log(e, p.getId(), CombatEventType.ACTION, p.getName() + " perdeu a base ativa");
        }
        if (down || GRABBED.equals(key) || RESTRAINED.equals(key)) {
            martial.fire(e, p, sheetOf(p), down ? "KNOCKED_DOWN" : "GRAPPLED", null, null);
        }
    }

    public EncounterResponse patchCounter(UUID encounterId, UUID counterId, int value, UUID userId) {
        Encounter e = load(encounterId);
        boolean master = requireMembership(e.getCampaignId(), userId) == Role.MASTER;
        requireNotEnded(e);
        List<EncounterParticipant> ps = participants.findByEncounterIdOrderByOrderIndex(e.getId());
        CombatCounter c = martial.countersOf(ps.stream().map(EncounterParticipant::getId).toList()).stream()
                .filter(x -> x.getId().equals(counterId)).findFirst()
                .orElseThrow(() -> new EncounterNotFoundException("Contador não encontrado"));
        EncounterParticipant p = participantOf(e, c.getParticipantId());
        if (!master && !userId.equals(ownerUserId(sheetOf(p)))) {
            throw new AccessDeniedException("Você só pode agir pelo seu próprio personagem");
        }
        martial.set(e, p, c, value);
        return done(e, "COUNTER", userId, master);
    }

    public EncounterResponse removeCondition(UUID encounterId, UUID conditionId, UUID userId) {
        Encounter e = loadAsMaster(encounterId, userId);
        requireNotEnded(e);
        List<EncounterParticipant> ps = participants.findByEncounterIdOrderByOrderIndex(e.getId());
        Map<UUID, EncounterParticipant> byId = ps.stream().collect(Collectors.toMap(EncounterParticipant::getId, Function.identity()));
        CombatCondition c = conditions.findByParticipantIdIn(byId.keySet()).stream()
                .filter(x -> x.getId().equals(conditionId)).findFirst()
                .orElseThrow(() -> new EncounterNotFoundException("Condição não encontrada"));
        EncounterParticipant p = byId.get(c.getParticipantId());
        conditions.delete(c);
        String name = catalog.find(c.getConditionKey()).map(ConditionCatalog.Def::name).orElse(c.getConditionKey());
        log(e, p.getId(), CombatEventType.CONDITION_REMOVED, "Mestre removeu " + name + " de " + p.getName());
        return done(e, "CONDITION", userId, true);
    }

    public EncounterResponse end(UUID encounterId, UUID userId) {
        Encounter e = loadAsMaster(encounterId, userId);
        requireNotEnded(e);
        VitalService.Ctx ctx = VitalService.Ctx.of(userId, ActorRole.MASTER, "Fim do combate");
        for (EncounterParticipant p : participants.findByEncounterIdOrderByOrderIndex(e.getId())) {
            vital.onCombatEnd(e, p, sheetOf(p), ctx);
            martial.fire(e, p, sheetOf(p), "COMBAT_END", null, null);
        }
        e.setStatus(EncounterStatus.ENDED);
        e.setEndedAt(java.time.Instant.now());
        log(e, null, CombatEventType.END, "Combate encerrado: " + e.getName());
        return done(e, "END", userId, true);
    }
}
