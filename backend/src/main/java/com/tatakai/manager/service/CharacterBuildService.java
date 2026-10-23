package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.AttributeAssignmentRequest;
import com.tatakai.manager.dto.request.BuildBackRequest;
import com.tatakai.manager.dto.request.BuildIdentityRequest;
import com.tatakai.manager.dto.request.ClanChoiceRequest;
import com.tatakai.manager.dto.request.FreePointsRequest;
import com.tatakai.manager.dto.request.ReopenBuildRequest;
import com.tatakai.manager.dto.request.ResolveTraitSlotRequest;
import com.tatakai.manager.dto.request.StartBuildRequest;
import com.tatakai.manager.dto.request.TraitChoiceRequest;
import com.tatakai.manager.dto.response.CharacterBuildResponse;
import com.tatakai.manager.dto.response.CharacterSheetResponse;
import com.tatakai.manager.dto.response.ClanResponse;
import com.tatakai.manager.dto.response.SheetUpdateMessage;
import com.tatakai.manager.dto.response.SheetUpdateMessage.SheetEvent;
import com.tatakai.manager.entity.ActivityCategory;
import com.tatakai.manager.entity.ActorRole;
import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.BodyType;
import com.tatakai.manager.entity.BuildGains;
import com.tatakai.manager.entity.BuildSettings;
import com.tatakai.manager.entity.BuildState;
import com.tatakai.manager.entity.BuildStatus;
import com.tatakai.manager.entity.BuildStep;
import com.tatakai.manager.entity.CampaignMember;
import com.tatakai.manager.entity.CharacterBuild;
import com.tatakai.manager.entity.CharacterClan;
import com.tatakai.manager.entity.CharacterSheet;
import com.tatakai.manager.entity.CharacterTrait;
import com.tatakai.manager.entity.Clan;
import com.tatakai.manager.entity.Dominance;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.entity.Roll;
import com.tatakai.manager.entity.TraitAcquisition;
import com.tatakai.manager.entity.TraitDefinition;
import com.tatakai.manager.entity.TraitRarity;
import com.tatakai.manager.entity.TraitSlot;
import com.tatakai.manager.entity.TraitSlotStatus;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.CharacterSheetAlreadyExistsException;
import com.tatakai.manager.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.exception.InvalidBuildStepException;
import com.tatakai.manager.exception.SheetLockedException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CharacterBuildRepository;
import com.tatakai.manager.repository.CharacterSheetRepository;
import com.tatakai.manager.service.rules.BuildRules;
import com.tatakai.manager.service.rules.BuildRules.AttributeLayer;
import com.tatakai.manager.service.rules.SheetRules;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Construção Inicial (wizard) da ficha: passos travados, rolagens no servidor, regras de §11.4. */
@Service
public class CharacterBuildService {

    private static final int MAX_CLAN_ATTEMPTS = 500;

    private final CharacterBuildRepository buildRepository;
    private final CharacterSheetRepository sheetRepository;
    private final CampaignMemberRepository memberRepository;
    private final CampaignRuleService ruleService;
    private final CatalogService catalog;
    private final RollService rollService;
    private final SheetActivityLogService activityLog;
    private final ApplicationEventPublisher events;
    private final CharacterSheetService sheetService;

    // ponytail: CharacterSheetService entra no fim do construtor só para o toResponseFor do submit (sem ciclo).
    public CharacterBuildService(CharacterBuildRepository buildRepository,
                                 CharacterSheetRepository sheetRepository,
                                 CampaignMemberRepository memberRepository,
                                 CampaignRuleService ruleService,
                                 CatalogService catalog,
                                 RollService rollService,
                                 SheetActivityLogService activityLog,
                                 ApplicationEventPublisher events,
                                 CharacterSheetService sheetService) {
        this.buildRepository = buildRepository;
        this.sheetRepository = sheetRepository;
        this.memberRepository = memberRepository;
        this.ruleService = ruleService;
        this.catalog = catalog;
        this.rollService = rollService;
        this.activityLog = activityLog;
        this.events = events;
        this.sheetService = sheetService;
    }

    /** Contexto de uma requisição: ficha, construção e quem age. */
    private record Ctx(CharacterSheet sheet, CharacterBuild build, UUID campaignId, UUID actorId,
                       ActorRole role, String actorName, boolean master, boolean owner) {
        BuildState state() {
            return build.getState();
        }

        BuildSettings settings() {
            return build.getSettings();
        }
    }

    private record Gains(List<TraitDefinition> traits, List<Clan> clans) {
        List<BuildGains> traitGains() {
            return traits.stream().map(TraitDefinition::getBuildGains).toList();
        }

        List<BuildGains> clanGains() {
            return clans.stream().map(Clan::getBuildGains).toList();
        }

        List<BuildGains> all() {
            List<BuildGains> all = new ArrayList<>(clanGains());
            all.addAll(traitGains());
            return all;
        }
    }

    // ---------------------------------------------------------------- início e leitura

    @Transactional
    public CharacterBuildResponse start(UUID campaignId, StartBuildRequest req, UUID requesterId) {
        CampaignMember member = memberRepository.findByCampaignIdAndUserId(campaignId, requesterId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"));
        if (member.getRole() != Role.PLAYER) {
            throw new AccessDeniedException("Apenas jogadores têm ficha de personagem");
        }
        if (sheetRepository.existsByCampaignMemberId(member.getId())) {
            throw new CharacterSheetAlreadyExistsException();
        }

        // valores provisórios: a ficha só recebe os números reais no envio (submit)
        CharacterSheet saved = sheetRepository.save(CharacterSheet.builder()
                .campaignMember(member)
                .name(req.name())
                .buildStatus(BuildStatus.IN_BUILD)
                .bodyType(BodyType.MESOMORPH)
                .dominance(Dominance.RIGHT)
                .heightM(1.70)
                .weightKg(60)
                .build());
        CharacterBuild build = buildRepository.save(CharacterBuild.builder()
                .sheet(saved)
                .step(BuildStep.IDENTITY)
                .settings(ruleService.buildSettings(campaignId))
                .state(new BuildState())
                .build());

        Ctx c = new Ctx(saved, build, campaignId, requesterId, ActorRole.PLAYER,
                member.getUser().getName(), false, true);
        activityLog.record(saved, requesterId, ActorRole.PLAYER, ActivityCategory.BUILD, "START_BUILD",
                null, null, saved.getName(), null, null);
        publish(SheetEvent.SHEET_CREATED, c, "START_BUILD");
        return response(c);
    }

    @Transactional(readOnly = true)
    public CharacterBuildResponse get(UUID sheetId, UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        if (!c.owner() && !c.master()) {
            throw new AccessDeniedException("Você não pode ver a construção desta ficha");
        }
        return response(c);
    }

    // ---------------------------------------------------------------- passo 1: identidade

    @Transactional
    public CharacterBuildResponse setIdentity(UUID sheetId, BuildIdentityRequest req, UUID requesterId) {
        Ctx c = ownerInBuild(sheetId, requesterId);
        requireStep(c, BuildStep.IDENTITY);
        CharacterSheet s = c.sheet();
        s.setBodyType(req.bodyType());
        s.setDominance(req.dominance());
        s.setHeightM(req.heightM());
        if (req.weightKg() != null) {
            s.setWeightKg(req.weightKg());
        } else {
            Map<Attribute, AttributeLayer> layers = layers(c, gains(c.state()), true);
            int con = layers == null ? 0 : layers.get(Attribute.CON).finalValue();
            s.setWeightKg(Math.round(SheetRules.initialWeight(req.heightM(), req.bodyType(), con) * 10) / 10.0);
        }
        return finish(c, "SET_IDENTITY", null);
    }

    // ---------------------------------------------------------------- passo 2: traços

    @Transactional
    public CharacterBuildResponse rollTraits(UUID sheetId, UUID requesterId) {
        Ctx c = ownerInBuild(sheetId, requesterId);
        requireStep(c, BuildStep.TRAITS);
        BuildState st = c.state();
        BuildSettings se = c.settings();
        if ("BALANCED_CHOICE".equals(se.traitMethod())) {
            throw new InvalidBuildStepException("Este método usa escolha de traços, não rolagem");
        }
        if (st.getTraitCountRoll() != null) {
            throw new InvalidBuildStepException("Os traços já foram rolados; só o Mestre pode rerrolar");
        }
        boolean prodigy = "PRODIGY".equals(se.traitMethod());
        boolean divineOn100 = "ROLL_100_GRANTS_DIVINE".equals(se.divineMode());

        int count = prodigy
                ? d(c, "1d2", "Construção: quantidade de traços (Prodígio, 1d2+1)") + 1
                : d(c, "1d4", "Construção: quantidade de traços");
        st.setTraitCountRoll(count);

        List<TraitDefinition> cat = catalog.traitsForCampaign(c.campaignId());
        Set<UUID> used = new HashSet<>();
        List<TraitSlot> slots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TraitSlot slot = new TraitSlot();
            slot.setIndex(i);
            int r = d(c, "1d100", "Construção: raridade do traço " + (i + 1));
            slot.setRarityRoll(r);
            TraitRarity rarity = BuildRules.rarityFor(r, divineOn100);
            slot.setRarity(prodigy ? BuildRules.promoteForProdigy(rarity) : rarity);
            slot.setTableRoll(d(c, tableDie(slot.getRarity()),
                    "Construção: tabela de traços " + rarityName(slot.getRarity()) + " (traço " + (i + 1) + ")"));
            resolveByTable(slot, cat, used);
            slots.add(slot);
        }
        if ("FREE_DIVINE_TRAIT".equals(se.divineMode())) {
            slots.add(divineSlot(c, count, cat, used));
        }
        st.setTraitSlots(slots);
        st.setTraitsConfirmed(false);
        return finish(c, "ROLL_TRAITS", null);
    }

    @Transactional
    public CharacterBuildResponse chooseTraits(UUID sheetId, TraitChoiceRequest req, UUID requesterId) {
        Ctx c = ownerInBuild(sheetId, requesterId);
        requireStep(c, BuildStep.TRAITS);
        BuildState st = c.state();
        BuildSettings se = c.settings();
        if (!"BALANCED_CHOICE".equals(se.traitMethod())) {
            throw new InvalidBuildStepException("Este método usa rolagem de traços, não escolha");
        }
        List<UUID> ids = req.traitIds();
        if (ids.size() != 3 || new HashSet<>(ids).size() != 3) {
            throw new InvalidBuildStepException("Escolha exatamente 3 traços diferentes: 1 Comum, 1 Raro e 1 Épico");
        }
        List<TraitDefinition> cat = catalog.traitsForCampaign(c.campaignId());
        Map<UUID, TraitDefinition> byId = cat.stream().collect(Collectors.toMap(TraitDefinition::getId, t -> t, (a, b) -> a));
        Set<TraitRarity> rarities = new HashSet<>();
        List<TraitSlot> slots = new ArrayList<>();
        for (UUID id : ids) {
            TraitDefinition t = byId.get(id);
            if (t == null) throw new InvalidBuildStepException("Traço indisponível nesta campanha");
            rarities.add(t.getRarity());
            TraitSlot slot = new TraitSlot();
            slot.setIndex(slots.size());
            slot.setRarity(t.getRarity());
            slot.setTraitId(t.getId());
            slot.setTraitName(t.getName());
            slot.setStatus(TraitSlotStatus.RESOLVED);
            slots.add(slot);
        }
        if (!rarities.equals(Set.of(TraitRarity.COMMON, TraitRarity.RARE, TraitRarity.EPIC))) {
            throw new InvalidBuildStepException("Escolha exatamente 1 traço Comum, 1 Raro e 1 Épico");
        }
        if ("FREE_DIVINE_TRAIT".equals(se.divineMode())) {
            TraitSlot previous = st.getTraitSlots().stream().filter(TraitSlot::isDivineBonus).findFirst().orElse(null);
            if (previous != null) { // dado já rolado é mantido
                previous.setIndex(slots.size());
                slots.add(previous);
            } else {
                Set<UUID> used = ids.stream().collect(Collectors.toCollection(HashSet::new));
                slots.add(divineSlot(c, slots.size(), cat, used));
            }
        }
        st.setTraitSlots(slots);
        st.setTraitsConfirmed(false);
        return finish(c, "CHOOSE_TRAITS", null);
    }

    @Transactional
    public CharacterBuildResponse resolveTraitSlot(UUID sheetId, int index, ResolveTraitSlotRequest req,
                                                   UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        if (!c.master()) throw new AccessDeniedException("Apenas o Mestre pode realizar esta ação");
        requireInBuild(c);
        BuildState st = c.state();
        TraitSlot slot = st.getTraitSlots().stream().filter(x -> x.getIndex() == index).findFirst()
                .orElseThrow(() -> new InvalidBuildStepException("Slot de traço inválido"));
        if (slot.getStatus() != TraitSlotStatus.PENDING_GM) {
            throw new InvalidBuildStepException("Este slot não está aguardando o Mestre");
        }
        Set<UUID> others = st.getTraitSlots().stream().filter(x -> x != slot)
                .map(TraitSlot::getTraitId).filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));

        String action;
        if (Boolean.TRUE.equals(req.reroll())) {
            if (!c.settings().gmRerolls()) {
                throw new InvalidBuildStepException("Esta campanha não permite rerrolagem do Mestre");
            }
            action = "REROLL_TRAIT_SLOT";
            slot.setTableRoll(d(c, tableDie(slot.getRarity()),
                    "Construção: rerrolagem do traço " + (index + 1) + " (Mestre)"));
            resolveByTable(slot, catalog.traitsForCampaign(c.campaignId()), others);
            slot.setNote(slot.getTraitId() == null ? slot.getNote() : req.reason());
        } else {
            if (req.traitId() == null) {
                throw new InvalidBuildStepException("Informe o traço de reposição ou peça uma rerrolagem");
            }
            TraitDefinition t = catalog.traitsForCampaign(c.campaignId()).stream()
                    .filter(x -> req.traitId().equals(x.getId())).findFirst()
                    .orElseThrow(() -> new InvalidBuildStepException("Traço indisponível nesta campanha"));
            if (others.contains(t.getId())) {
                throw new InvalidBuildStepException("Este traço já foi concedido em outro slot");
            }
            action = "RESOLVE_TRAIT_SLOT";
            slot.setTraitId(t.getId());
            slot.setTraitName(t.getName());
            slot.setStatus(TraitSlotStatus.RESOLVED);
            slot.setNote(req.reason());
        }
        return finish(c, action, "slot:" + index, req.reason());
    }

    // ---------------------------------------------------------------- passo 3: teste de sangue

    @Transactional
    public CharacterBuildResponse rollBloodTest(UUID sheetId, UUID requesterId) {
        Ctx c = ownerInBuild(sheetId, requesterId);
        requireStep(c, BuildStep.BLOOD_TEST);
        BuildState st = c.state();
        if (st.getBloodTestRoll() != null) {
            throw new InvalidBuildStepException("O Teste de Sangue já foi rolado");
        }
        st.setBloodTestRoll(d(c, "1d20", "Construção: Teste de Sangue"));
        st.setClanSlots(BuildRules.clanSlotsFor(st.getBloodTestRoll(), lowThreshold(c.settings(), gains(st))));
        st.setClanIds(new ArrayList<>());
        st.setClanRolls(new ArrayList<>());
        st.setClansConfirmed(false);
        return finish(c, "ROLL_BLOOD_TEST", null);
    }

    @Transactional
    public CharacterBuildResponse chooseClans(UUID sheetId, ClanChoiceRequest req, UUID requesterId) {
        Ctx c = ownerInBuild(sheetId, requesterId);
        requireStep(c, BuildStep.BLOOD_TEST);
        BuildState st = c.state();
        if (st.getBloodTestRoll() == null) throw new InvalidBuildStepException("Role o Teste de Sangue primeiro");
        if (c.settings().randomClan()) {
            throw new InvalidBuildStepException("Esta campanha sorteia o clã; use a rolagem de clãs");
        }
        List<UUID> ids = req.clanIds();
        if (ids.size() != st.getClanSlots() || new HashSet<>(ids).size() != ids.size()) {
            throw new InvalidBuildStepException("Escolha exatamente " + st.getClanSlots() + " clã(s) diferente(s)");
        }
        Set<UUID> available = catalog.clansForCampaign(c.campaignId()).stream()
                .map(Clan::getId).collect(Collectors.toSet());
        if (!available.containsAll(ids)) throw new InvalidBuildStepException("Clã indisponível nesta campanha");
        st.setClanIds(new ArrayList<>(ids));
        st.setClanRolls(new ArrayList<>());
        st.setClansConfirmed(false);
        return finish(c, "CHOOSE_CLANS", null);
    }

    @Transactional
    public CharacterBuildResponse rollClans(UUID sheetId, UUID requesterId) {
        Ctx c = ownerInBuild(sheetId, requesterId);
        requireStep(c, BuildStep.BLOOD_TEST);
        BuildState st = c.state();
        if (!c.settings().randomClan()) {
            throw new InvalidBuildStepException("Esta campanha não sorteia clãs");
        }
        if (st.getBloodTestRoll() == null) throw new InvalidBuildStepException("Role o Teste de Sangue primeiro");
        int missing = st.getClanSlots() - st.getClanIds().size();
        if (missing <= 0) throw new InvalidBuildStepException("Não há clãs a rolar");

        Map<Integer, Clan> byNumber = new HashMap<>();
        for (Clan cl : catalog.clansForCampaign(c.campaignId())) {
            if (cl.getRollNumber() != null && !st.getClanIds().contains(cl.getId())) {
                byNumber.putIfAbsent(cl.getRollNumber(), cl);
            }
        }
        if (byNumber.size() < missing) {
            throw new InvalidBuildStepException("O catálogo não tem clãs sorteáveis suficientes");
        }
        for (int attempts = 0; missing > 0; attempts++) {
            if (attempts >= MAX_CLAN_ATTEMPTS) {
                throw new InvalidBuildStepException("Não foi possível sortear o clã; tente novamente");
            }
            int r = d(c, "1d20", "Construção: sorteio de clã");
            Clan cl = byNumber.remove(r); // sem match ou repetido: rerrola
            if (cl == null) continue;
            st.getClanIds().add(cl.getId());
            st.getClanRolls().add(r);
            missing--;
        }
        st.setClansConfirmed(false);
        return finish(c, "ROLL_CLANS", null);
    }

    // ---------------------------------------------------------------- passo 4: atributos

    @Transactional
    public CharacterBuildResponse rollAttributes(UUID sheetId, UUID requesterId) {
        Ctx c = ownerInBuild(sheetId, requesterId);
        requireStep(c, BuildStep.ATTRIBUTES);
        BuildState st = c.state();
        if (!st.getAttributeDice().isEmpty()) {
            throw new InvalidBuildStepException("Os atributos já foram rolados");
        }
        boolean random = c.settings().randomAttributes();
        Roll r = rollService.rollInternal(c.sheet(), random ? "7d20" : "7d12",
                "Construção: atributos (" + (random ? "7d20 em ordem" : "7d12") + ")", c.actorId(), c.role());
        st.setAttributeDice(diceOf(r));
        st.setAssignment(new EnumMap<>(Attribute.class));
        st.setAttributesConfirmed(false);
        return finish(c, "ROLL_ATTRIBUTES", null);
    }

    @Transactional
    public CharacterBuildResponse assignAttributes(UUID sheetId, AttributeAssignmentRequest req, UUID requesterId) {
        Ctx c = ownerInBuild(sheetId, requesterId);
        requireStep(c, BuildStep.ATTRIBUTES);
        BuildState st = c.state();
        if (c.settings().randomAttributes()) {
            throw new InvalidBuildStepException("Os atributos aleatórios seguem a ordem fixa; não há distribuição");
        }
        if (st.getAttributeDice().size() != Attribute.values().length) {
            throw new InvalidBuildStepException("Role os atributos primeiro");
        }
        Map<Attribute, Integer> a = req.assignment();
        if (a.size() != Attribute.values().length || a.containsValue(null) || !isPermutation(a.values())) {
            throw new InvalidBuildStepException("Distribua cada dado em um atributo diferente");
        }
        st.setAssignment(new EnumMap<>(a));
        st.setAttributesConfirmed(false);
        return finish(c, "ASSIGN_ATTRIBUTES", null);
    }

    // ---------------------------------------------------------------- passo 6: pontos livres

    @Transactional
    public CharacterBuildResponse setFreePoints(UUID sheetId, FreePointsRequest req, UUID requesterId) {
        Ctx c = ownerInBuild(sheetId, requesterId);
        requireStep(c, BuildStep.FREE_POINTS);
        if (c.settings().randomAttributes()) {
            throw new InvalidBuildStepException("Atributos aleatórios não têm pontos livres");
        }
        Map<Attribute, Integer> fp = new EnumMap<>(Attribute.class);
        req.freePoints().forEach((k, v) -> {
            if (v != null && v != 0) fp.put(k, v);
        });
        validateFreePoints(c, fp);
        c.state().setFreePoints(fp);
        c.state().setFreePointsConfirmed(false);
        return finish(c, "SET_FREE_POINTS", null);
    }

    // ---------------------------------------------------------------- navegação

    @Transactional
    public CharacterBuildResponse confirmStep(UUID sheetId, BuildStep step, UUID requesterId) {
        Ctx c = ownerInBuild(sheetId, requesterId);
        requireStep(c, step);
        BuildState st = c.state();
        BuildStep next;
        switch (step) {
            case IDENTITY -> next = BuildStep.TRAITS;
            case TRAITS -> {
                if (st.getTraitSlots().isEmpty()) throw new InvalidBuildStepException("Defina os traços primeiro");
                if (st.getTraitSlots().stream().anyMatch(s -> s.getTraitId() == null)) {
                    throw new InvalidBuildStepException("Há traços aguardando o Mestre");
                }
                st.setTraitsConfirmed(true);
                recomputeClans(c); // traços novos podem mudar o limiar do Teste de Sangue
                next = BuildStep.BLOOD_TEST;
            }
            case BLOOD_TEST -> {
                if (st.getBloodTestRoll() == null) throw new InvalidBuildStepException("Role o Teste de Sangue");
                if (st.getClanIds().size() != st.getClanSlots()) {
                    throw new InvalidBuildStepException("Escolha ou sorteie " + st.getClanSlots() + " clã(s)");
                }
                st.setClansConfirmed(true);
                next = BuildStep.ATTRIBUTES;
            }
            case ATTRIBUTES -> {
                if (st.getAttributeDice().size() != Attribute.values().length) {
                    throw new InvalidBuildStepException("Role os atributos");
                }
                boolean random = c.settings().randomAttributes();
                if (!random && !(st.getAssignment().size() == Attribute.values().length
                        && isPermutation(st.getAssignment().values()))) {
                    throw new InvalidBuildStepException("Distribua todos os dados nos atributos");
                }
                st.setAttributesConfirmed(true);
                if (random) { // sem pontos livres: pula o passo
                    st.setFreePoints(new EnumMap<>(Attribute.class));
                    st.setFreePointsConfirmed(true);
                }
                next = random ? BuildStep.REVIEW : BuildStep.FREE_POINTS;
            }
            case FREE_POINTS -> {
                validateFreePoints(c, st.getFreePoints()); // traços/clãs podem ter mudado desde a escolha
                st.setFreePointsConfirmed(true);
                next = BuildStep.REVIEW;
            }
            default -> throw new InvalidBuildStepException("Use o envio para concluir a construção");
        }
        c.build().setStep(next);
        return finish(c, "CONFIRM_STEP", "step", step, next, null);
    }

    @Transactional
    public CharacterBuildResponse back(UUID sheetId, BuildBackRequest req, UUID requesterId) {
        Ctx c = ownerInBuild(sheetId, requesterId);
        BuildStep target = req.step();
        BuildStep from = c.build().getStep();
        if (target.ordinal() >= from.ordinal()) {
            throw new InvalidBuildStepException("Só é possível voltar a um passo anterior");
        }
        BuildState st = c.state();
        // dados já rolados ficam; só as confirmações do passo alvo em diante caem
        if (target.ordinal() <= BuildStep.TRAITS.ordinal()) st.setTraitsConfirmed(false);
        if (target.ordinal() <= BuildStep.BLOOD_TEST.ordinal()) st.setClansConfirmed(false);
        if (target.ordinal() <= BuildStep.ATTRIBUTES.ordinal()) st.setAttributesConfirmed(false);
        if (target.ordinal() <= BuildStep.FREE_POINTS.ordinal()) st.setFreePointsConfirmed(false);
        c.build().setStep(target);
        return finish(c, "BACK_STEP", "step", from, target, null);
    }

    @Transactional
    public CharacterBuildResponse reopen(UUID sheetId, ReopenBuildRequest req, UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        if (!c.master()) throw new AccessDeniedException("Apenas o Mestre pode realizar esta ação");
        if (c.sheet().getBuildStatus() != BuildStatus.COMPLETED) {
            throw new SheetLockedException("Apenas fichas concluídas podem ter a construção reaberta");
        }
        BuildStatus before = c.sheet().getBuildStatus();
        c.sheet().setBuildStatus(BuildStatus.IN_BUILD);
        c.build().setStep(BuildStep.REVIEW);
        return finish(c, "REOPEN_BUILD", "buildStatus", before, BuildStatus.IN_BUILD, req.reason());
    }

    // ---------------------------------------------------------------- envio

    @Transactional
    public CharacterSheetResponse submit(UUID sheetId, UUID requesterId) {
        Ctx c = ownerInBuild(sheetId, requesterId);
        requireStep(c, BuildStep.REVIEW);
        BuildState st = c.state();
        if (!(st.isTraitsConfirmed() && st.isClansConfirmed() && st.isAttributesConfirmed()
                && st.isFreePointsConfirmed())) {
            throw new InvalidBuildStepException("Há passos não confirmados");
        }
        Gains g = gains(st);
        long wantedTraits = st.getTraitSlots().size();
        if (g.traits().size() != wantedTraits || g.clans().size() != st.getClanIds().size()) {
            throw new InvalidBuildStepException("Um traço ou clã escolhido não existe mais no catálogo; refaça o passo");
        }
        Map<Attribute, AttributeLayer> layers = layers(c, g, true);
        if (layers == null) throw new InvalidBuildStepException("Atributos incompletos");

        CharacterSheet s = c.sheet();
        for (Attribute a : Attribute.values()) {
            s.setAttribute(a, layers.get(a).finalValue());
        }
        BuildRules.ResourceMaxes res = BuildRules.resources(layers.get(Attribute.CON).finalValue(), g.all());
        s.setHpMax(res.hpMax());
        s.setSpMax(res.spMax());
        s.setKpMax(res.kpMax());
        s.setCurrentHp(res.hpMax());
        s.setCurrentSp(res.spMax());
        s.setCurrentKp(res.kpMax());
        s.setLimiterAdjustments(new HashMap<>(BuildRules.limiterAdjustments(g.all())));

        // reenvio após reabertura: troca só o que veio da construção (traços de campanha ficam)
        s.getTraits().removeIf(t -> t.getAcquiredIn() == TraitAcquisition.INITIAL_BUILD);
        s.getClans().clear();
        for (TraitDefinition t : g.traits()) {
            s.getTraits().add(CharacterTrait.builder().sheet(s).traitId(t.getId()).traitVersion(t.getVersion())
                    .name(t.getName()).rarity(t.getRarity()).category(t.getCategory())
                    .description(t.getDescription()).buildGains(t.getBuildGains())
                    .acquiredIn(TraitAcquisition.INITIAL_BUILD).gainsApplied(true).build());
        }
        for (Clan cl : g.clans()) {
            s.getClans().add(CharacterClan.builder().sheet(s).clanId(cl.getId()).clanVersion(cl.getVersion())
                    .name(cl.getName()).buildGains(cl.getBuildGains()).build());
        }
        BuildStatus before = s.getBuildStatus();
        s.setBuildStatus(BuildStatus.PENDING_APPROVAL);
        CharacterSheet saved = sheetRepository.save(s);
        buildRepository.save(c.build());

        activityLog.record(saved, requesterId, c.role(), ActivityCategory.BUILD, "SUBMIT_BUILD",
                "buildStatus", before, saved.getBuildStatus(), null, null);
        publish(SheetEvent.SHEET_UPDATED, c, "SUBMIT_BUILD");
        return sheetService.toResponseFor(saved, requesterId);
    }

    // ---------------------------------------------------------------- acesso e guardas

    private Ctx load(UUID sheetId, UUID requesterId) {
        CharacterSheet sheet = sheetRepository.findById(sheetId).orElseThrow(CharacterSheetNotFoundException::new);
        if (sheet.isNpcSheet()) throw new CharacterSheetNotFoundException();
        UUID campaignId = sheet.getCampaignMember().getCampaign().getId();
        CampaignMember m = memberRepository.findByCampaignIdAndUserId(campaignId, requesterId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"));
        CharacterBuild build = buildRepository.findById(sheetId).orElseThrow(CharacterSheetNotFoundException::new);
        if (build.getState() == null) build.setState(new BuildState());
        boolean master = m.getRole() == Role.MASTER;
        boolean owner = sheet.getCampaignMember().getUser().getId().equals(requesterId);
        return new Ctx(sheet, build, campaignId, requesterId, master ? ActorRole.MASTER : ActorRole.PLAYER,
                m.getUser().getName(), master, owner);
    }

    /** Mutação do jogador dono, só enquanto a ficha está em construção. */
    private Ctx ownerInBuild(UUID sheetId, UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        if (!c.owner()) throw new AccessDeniedException("Apenas o dono da ficha pode realizar esta ação");
        requireInBuild(c);
        return c;
    }

    private void requireInBuild(Ctx c) {
        if (c.sheet().getBuildStatus() != BuildStatus.IN_BUILD) {
            throw new SheetLockedException("A construção desta ficha já foi encerrada");
        }
    }

    private void requireStep(Ctx c, BuildStep expected) {
        BuildStep current = c.build().getStep();
        if (current != expected) {
            throw new InvalidBuildStepException(
                    "Ação do passo " + expected + " não permitida agora (passo atual: " + current + ")");
        }
    }

    // ---------------------------------------------------------------- regras de apoio

    private int d(Ctx c, String expression, String purpose) {
        return rollService.rollInternal(c.sheet(), expression, purpose, c.actorId(), c.role()).getTotal();
    }

    private static List<Integer> diceOf(Roll r) {
        String raw = r.getDiceResults().replace("[", "").replace("]", "");
        List<Integer> out = new ArrayList<>();
        for (String p : raw.split(",")) {
            if (!p.isBlank()) out.add(Integer.parseInt(p.trim()));
        }
        return out;
    }

    private static String tableDie(TraitRarity r) {
        return r == TraitRarity.DIVINE ? "1d10" : "1d20";
    }

    private static String rarityName(TraitRarity r) {
        return switch (r) {
            case COMMON -> "Comum";
            case RARE -> "Raro";
            case EPIC -> "Épico";
            case LEGENDARY -> "Lendário";
            case DIVINE -> "Divino";
        };
    }

    /** Resolve o slot pelo catálogo; sem traço, repetido ou Lendário 20, fica com o Mestre. */
    private void resolveByTable(TraitSlot slot, List<TraitDefinition> cat, Set<UUID> used) {
        slot.setTraitId(null);
        slot.setTraitName(null);
        Integer roll = slot.getTableRoll();
        Optional<TraitDefinition> match = cat.stream()
                .filter(t -> t.getRarity() == slot.getRarity() && roll != null && roll.equals(t.getRollNumber()))
                .findFirst();
        if (slot.getRarity() == TraitRarity.LEGENDARY && Integer.valueOf(20).equals(roll)) {
            pending(slot, "Lendário 20: o Mestre escolhe a reposição \"X\" ou manda rerrolar");
        } else if (match.isEmpty()) {
            pending(slot, "Nenhum traço no catálogo para esta rolagem");
        } else if (used.contains(match.get().getId())) {
            pending(slot, "Traço repetido");
        } else {
            slot.setTraitId(match.get().getId());
            slot.setTraitName(match.get().getName());
            slot.setStatus(TraitSlotStatus.ROLLED);
            slot.setNote(null);
            used.add(match.get().getId());
        }
    }

    private static void pending(TraitSlot slot, String note) {
        slot.setStatus(TraitSlotStatus.PENDING_GM);
        slot.setNote(note);
    }

    private TraitSlot divineSlot(Ctx c, int index, List<TraitDefinition> cat, Set<UUID> used) {
        TraitSlot slot = new TraitSlot();
        slot.setIndex(index);
        slot.setRarity(TraitRarity.DIVINE);
        slot.setDivineBonus(true);
        slot.setTableRoll(d(c, "1d10", "Construção: traço divino gratuito"));
        resolveByTable(slot, cat, used);
        return slot;
    }

    private static boolean epicPlus(TraitRarity r) {
        return r == TraitRarity.EPIC || r == TraitRarity.LEGENDARY || r == TraitRarity.DIVINE;
    }

    private static boolean lowThreshold(BuildSettings se, Gains g) {
        return "CLASSIC".equals(se.traitMethod()) && g.traits().stream().noneMatch(t -> epicPlus(t.getRarity()));
    }

    /** Refaz o número de clãs do Teste de Sangue (mantendo o dado) quando os traços mudam. */
    private void recomputeClans(Ctx c) {
        BuildState st = c.state();
        if (st.getBloodTestRoll() == null) return;
        st.setClanSlots(BuildRules.clanSlotsFor(st.getBloodTestRoll(), lowThreshold(c.settings(), gains(st))));
        while (st.getClanIds().size() > st.getClanSlots()) {
            st.getClanIds().remove(st.getClanIds().size() - 1);
            if (!st.getClanRolls().isEmpty()) st.getClanRolls().remove(st.getClanRolls().size() - 1);
        }
        st.setClansConfirmed(false);
    }

    private static boolean isPermutation(Collection<Integer> values) {
        Set<Integer> expected = new HashSet<>();
        for (int i = 0; i < Attribute.values().length; i++) expected.add(i);
        return values.size() == expected.size() && new HashSet<>(values).equals(expected);
    }

    private Gains gains(BuildState st) {
        List<TraitDefinition> traits = new ArrayList<>();
        for (TraitSlot s : st.getTraitSlots()) {
            if (s.getTraitId() != null) catalog.findTrait(s.getTraitId()).ifPresent(traits::add);
        }
        List<Clan> clans = new ArrayList<>();
        for (UUID id : st.getClanIds()) {
            catalog.findClan(id).ifPresent(clans::add);
        }
        return new Gains(traits, clans);
    }

    /** Valores-base dos atributos a partir dos dados; null enquanto incompleto. */
    private Map<Attribute, Integer> baseAttributes(BuildSettings se, BuildState st) {
        List<Integer> dice = st.getAttributeDice();
        if (dice.size() != Attribute.values().length) return null;
        Map<Attribute, Integer> base = new EnumMap<>(Attribute.class);
        if (se.randomAttributes()) { // ordem estrita STR→DEX→CON→INT→CHA→MEN→TAL
            for (Attribute a : Attribute.values()) base.put(a, dice.get(a.ordinal()));
            return base;
        }
        if (st.getAssignment().size() != Attribute.values().length || !isPermutation(st.getAssignment().values())) {
            return null;
        }
        st.getAssignment().forEach((a, i) -> base.put(a, dice.get(i)));
        return base;
    }

    private Map<Attribute, AttributeLayer> layers(Ctx c, Gains g, boolean withFreePoints) {
        Map<Attribute, Integer> base = baseAttributes(c.settings(), c.state());
        if (base == null) return null;
        return BuildRules.layers(base, g.clanGains(), g.traitGains(),
                withFreePoints ? c.state().getFreePoints() : Map.of());
    }

    private int freePointsAvailable(Ctx c, Gains g) {
        return BuildRules.freePointsAvailable(c.sheet().getDominance(), g.all(), c.settings().randomAttributes());
    }

    private void validateFreePoints(Ctx c, Map<Attribute, Integer> fp) {
        Gains g = gains(c.state());
        Map<Attribute, AttributeLayer> layers = layers(c, g, false);
        if (layers == null) throw new InvalidBuildStepException("Atributos incompletos");
        int sum = 0;
        for (Map.Entry<Attribute, Integer> e : fp.entrySet()) {
            int v = e.getValue();
            if (v < 0) throw new InvalidBuildStepException("Pontos livres não podem ser negativos");
            if (v == 0) continue;
            sum += v;
            int after = layers.get(e.getKey()).afterPercent();
            if (BuildRules.crossesBarrier(after, after + v)) {
                throw new InvalidBuildStepException("Pontos livres não podem cruzar a barreira de "
                        + e.getKey() + " (" + after + " → " + (after + v) + ")");
            }
        }
        if (sum > freePointsAvailable(c, g)) {
            throw new InvalidBuildStepException("Pontos livres além do disponível");
        }
    }

    // ---------------------------------------------------------------- persistência, log e resposta

    private CharacterBuildResponse finish(Ctx c, String action, String reason) {
        return finish(c, action, null, null, null, reason);
    }

    private CharacterBuildResponse finish(Ctx c, String action, String field, String reason) {
        return finish(c, action, field, null, null, reason);
    }

    private CharacterBuildResponse finish(Ctx c, String action, String field, Object before, Object after,
                                          String reason) {
        c.build().setState(c.state());
        buildRepository.save(c.build());
        sheetRepository.save(c.sheet());
        activityLog.record(c.sheet(), c.actorId(), c.role(), ActivityCategory.BUILD, action,
                field, before, after, null, reason);
        publish(SheetEvent.SHEET_UPDATED, c, action);
        return response(c);
    }

    private void publish(SheetEvent event, Ctx c, String action) {
        events.publishEvent(new SheetUpdateMessage(event, c.campaignId(), c.sheet().getId(),
                ActivityCategory.BUILD, action, c.actorName()));
    }

    private CharacterBuildResponse response(Ctx c) {
        BuildState st = c.state();
        CharacterSheet s = c.sheet();
        Gains g = gains(st);
        Map<Attribute, AttributeLayer> layers = layers(c, g, true);
        List<ClanResponse> clans = g.clans().stream()
                .map(cl -> new ClanResponse(cl.getId(), cl.getCampaignId(), cl.getName(), cl.getRollNumber(),
                        cl.getDescription(), cl.getBuildGains(),
                        cl.getHeritage() == null ? List.of() : cl.getHeritage(), cl.getVersion()))
                .toList();
        BuildRules.ResourceMaxes resources = layers == null ? null
                : BuildRules.resources(layers.get(Attribute.CON).finalValue(), g.all());
        return new CharacterBuildResponse(s.getId(), s.getName(), c.build().getStep(), c.settings(),
                s.getBodyType(), s.getDominance(), s.getHeightM(), s.getWeightKg(),
                st.getTraitCountRoll(), st.getTraitSlots(), st.isTraitsConfirmed(),
                st.getBloodTestRoll(), st.getClanSlots(), clans, st.isClansConfirmed(),
                st.getAttributeDice(), st.getAssignment(), st.isAttributesConfirmed(),
                layers, freePointsAvailable(c, g), st.getFreePoints(), st.isFreePointsConfirmed(),
                resources, s.getBuildStatus());
    }
}
