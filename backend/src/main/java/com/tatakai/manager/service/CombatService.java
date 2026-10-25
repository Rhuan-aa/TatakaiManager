package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.AddConditionRequest;
import com.tatakai.manager.dto.request.CombatActionRequest;
import com.tatakai.manager.dto.request.SetHysteriaRequest;
import com.tatakai.manager.dto.request.UpdateEncounterRequest;
import com.tatakai.manager.dto.request.UpdateParticipantRequest;
import com.tatakai.manager.dto.request.VitalAdjustRequest;
import com.tatakai.manager.entity.CombatClassification;
import com.tatakai.manager.entity.Hysteria;
import com.tatakai.manager.service.rules.VitalRules;
import com.tatakai.manager.dto.response.CombatEventResponse;
import com.tatakai.manager.dto.response.CombatUpdateMessage;
import com.tatakai.manager.dto.response.EncounterResponse;
import com.tatakai.manager.dto.response.EncounterResponse.ConditionResponse;
import com.tatakai.manager.dto.response.EncounterResponse.CounterResponse;
import com.tatakai.manager.entity.CombatCounter;
import com.tatakai.manager.entity.Dominance;
import com.tatakai.manager.entity.CharacterTechnique;
import com.tatakai.manager.service.rules.MartialRules;
import com.tatakai.manager.dto.response.EncounterResponse.ParticipantResponse;
import com.tatakai.manager.dto.response.EncounterResponse.Resources;
import com.tatakai.manager.dto.response.EncounterSummaryResponse;
import com.tatakai.manager.entity.ActivityCategory;
import com.tatakai.manager.entity.ActorRole;
import com.tatakai.manager.entity.BuildStatus;
import com.tatakai.manager.entity.CharacterArtStatus;
import com.tatakai.manager.entity.CharacterSheet;
import com.tatakai.manager.entity.CombatAction;
import com.tatakai.manager.entity.CombatCondition;
import com.tatakai.manager.entity.CombatEvent;
import com.tatakai.manager.entity.CombatEventType;
import com.tatakai.manager.entity.ConditionExpiry;
import com.tatakai.manager.entity.Encounter;
import com.tatakai.manager.entity.EncounterParticipant;
import com.tatakai.manager.entity.EncounterStatus;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.exception.CombatConflictException;
import com.tatakai.manager.exception.EncounterNotFoundException;
import com.tatakai.manager.exception.InvalidCombatException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CampaignNpcRepository;
import com.tatakai.manager.repository.CharacterArtRepository;
import com.tatakai.manager.repository.CharacterSheetRepository;
import com.tatakai.manager.repository.PendingAttackRepository;
import com.tatakai.manager.entity.AttackStatus;
import com.tatakai.manager.repository.CombatConditionRepository;
import com.tatakai.manager.repository.CombatEventRepository;
import com.tatakai.manager.repository.EncounterParticipantRepository;
import com.tatakai.manager.repository.EncounterRepository;
import com.tatakai.manager.service.rules.CombatRules;
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
    private final Random random = new SecureRandom();

    public CombatService(EncounterRepository encounters, EncounterParticipantRepository participants,
                         CombatConditionRepository conditions, CombatEventRepository timeline,
                         CharacterSheetRepository sheets, CharacterArtRepository arts,
                         CampaignNpcRepository campaignNpcs, CampaignMemberRepository members,
                         RollService rolls, SheetActivityLogService activityLog, ConditionCatalog catalog,
                         ApplicationEventPublisher publisher, PendingAttackRepository attacks,
                         VitalService vital, MartialService martial) {
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
                    m == null ? null : m.arts(), m == null ? null : m.bases()));
        }
        UUID current = e.getStatus() == EncounterStatus.ACTIVE && e.getCurrentIndex() < ps.size()
                ? ps.get(e.getCurrentIndex()).getId() : null;
        return new EncounterResponse(e.getId(), e.getCampaignId(), e.getName(), e.getStatus(), e.getRound(),
                e.getTurnCounter(), current, out, e.getDefaultClassification(), e.getEndedAt());
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
        boolean dissociated = keys.contains(VitalService.DISSOCIATED);
        if (dissociated && (action == CombatAction.MINOR || action == CombatAction.MINOR_OFFENSIVE)) {
            throw new InvalidCombatException("Dissociado: Ações Menores estão bloqueadas");
        }
        int pa = CombatRules.paCost(action, Boolean.TRUE.equals(req.slow()));
        if (action == CombatAction.CHANGE_GUARD && s.getDominance() == Dominance.AMBIDEXTROUS
                && p.getGuardChangesThisTurn() == 0) {
            pa = 0; // ambidestro: a 1ª troca de guarda do turno é Ação Livre
        }
        if (action == CombatAction.CHANGE_BASE) {
            pa = CombatRules.baseChangeCost(p.getActiveBaseId() != null, req.baseId() != null);
        }
        int sp = VitalRules.adjustSp(CombatRules.spCost(action, req.spCost()),
                VitalRules.tier(p.getAdrenaline()), dissociated);
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
                double d = CombatRules.moveDistance(p.getMovesThisTurn(), s.getDex());
                text.append(" — Moveu ").append(d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d).replace('.', ',')).append(" m");
            }
            case STAND_UP -> {
                conditions.deleteAll(down);
                log(e, p.getId(), CombatEventType.CONDITION_REMOVED, p.getName() + " não está mais Derrubado");
            }
            case STANCH -> vital.stanch(e, p, stanchTarget, sheetOf(stanchTarget), ctx);
            case CHANGE_GUARD -> {
                p.setGuard(req.guard());
                p.setGuardChangedAt(java.time.Instant.now()); // reinicia o Timing
                p.setGuardChangesThisTurn(p.getGuardChangesThisTurn() + 1);
                text.append(" — guarda ").append(req.guard().label());
            }
            case CHANGE_BASE -> {
                p.setActiveBaseId(newBase == null ? null : newBase.getId());
                text.append(newBase == null ? " — sem base ativa" : " — base " + newBase.getName());
            }
            default -> { }
        }
        log(e, p.getId(), CombatEventType.ACTION, text.toString());
        return done(e, "ACTION", userId, master);
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
        CombatCondition c;
        if (existing == null) {
            c = CombatCondition.builder().participantId(p.getId()).conditionKey(def.key()).build();
        } else {
            c = existing;
            if (def.sums()) {
                c.setIntensity((c.getIntensity() == null ? 1 : c.getIntensity()) + (req.intensity() == null ? 1 : req.intensity()));
            }
        }
        if (existing == null || !def.sums()) c.setIntensity(req.intensity());
        c.setSourceParticipantId(req.sourceParticipantId());
        c.setExpiresAt(expiry);
        c.setAppliedTurn(e.getTurnCounter());
        c.setTurnsRemaining(turns);
        c.setNote(req.note());
        conditions.save(c);
        log(e, p.getId(), CombatEventType.CONDITION_ADDED, p.getName() + ": " + def.name()
                + (existing == null ? " aplicada" : def.sums() ? " acumulada" : " renovada"));
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
