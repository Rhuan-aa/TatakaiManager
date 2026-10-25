package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.ApproveCharacterSheetRequest;
import com.tatakai.manager.dto.request.AttributesPayload;
import com.tatakai.manager.dto.request.CreateCharacterSheetRequest;
import com.tatakai.manager.dto.request.CreateGmSheetRequest;
import com.tatakai.manager.dto.request.GmAdjustmentRequest;
import com.tatakai.manager.dto.request.InjuryPayload;
import com.tatakai.manager.dto.request.SetNpcSectionVisibilityRequest;
import com.tatakai.manager.dto.request.ItemPayload;
import com.tatakai.manager.dto.request.UpdateCharacterSheetRequest;
import com.tatakai.manager.dto.request.UpdateResourcesRequest;
import com.tatakai.manager.dto.response.CharacterClanResponse;
import com.tatakai.manager.dto.response.CharacterSheetResponse;
import com.tatakai.manager.dto.response.CharacterTraitResponse;
import com.tatakai.manager.dto.response.CharacterSheetSummaryResponse;
import com.tatakai.manager.dto.response.InjuryResponse;
import com.tatakai.manager.dto.response.InventoryItemResponse;
import com.tatakai.manager.dto.response.NpcSectionVisibilityResponse;
import com.tatakai.manager.dto.response.SheetDerived;
import com.tatakai.manager.dto.response.SheetFatigue;
import com.tatakai.manager.dto.response.SheetProgress;
import com.tatakai.manager.dto.response.SheetResources;
import com.tatakai.manager.dto.response.SheetUpdateMessage;
import com.tatakai.manager.dto.response.SheetUpdateMessage.SheetEvent;
import com.tatakai.manager.entity.ActivityCategory;
import com.tatakai.manager.entity.ActorRole;
import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.AttributeBand;
import com.tatakai.manager.entity.BodyType;
import com.tatakai.manager.entity.BuildStatus;
import com.tatakai.manager.entity.CampaignMember;
import com.tatakai.manager.entity.CampaignNpc;
import com.tatakai.manager.entity.Npc;
import com.tatakai.manager.entity.NpcSheetVisibility;
import com.tatakai.manager.entity.User;
import com.tatakai.manager.entity.CharacterSheet;
import com.tatakai.manager.entity.CharacterTrait;
import com.tatakai.manager.entity.Clan;
import com.tatakai.manager.entity.OptionalRule;
import com.tatakai.manager.entity.TraitAcquisition;
import com.tatakai.manager.entity.TraitDefinition;
import com.tatakai.manager.entity.CharacterSheetVisibility;
import com.tatakai.manager.entity.FatigueType;
import com.tatakai.manager.entity.InventoryItem;
import com.tatakai.manager.entity.PermanentInjury;
import com.tatakai.manager.entity.ResourceType;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.entity.SheetSection;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.CharacterSheetAlreadyExistsException;
import com.tatakai.manager.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.exception.InvalidCharacterSheetException;
import com.tatakai.manager.exception.NpcNotFoundException;
import com.tatakai.manager.exception.NpcSheetAlreadyExistsException;
import com.tatakai.manager.exception.SheetEntryNotFoundException;
import com.tatakai.manager.exception.SheetLockedException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CampaignNpcRepository;
import com.tatakai.manager.repository.CharacterBuildRepository;
import com.tatakai.manager.repository.NpcRepository;
import com.tatakai.manager.repository.NpcSheetVisibilityRepository;
import com.tatakai.manager.repository.UserRepository;
import com.tatakai.manager.repository.CharacterSheetRepository;
import com.tatakai.manager.repository.CharacterSheetVisibilityRepository;
import com.tatakai.manager.service.rules.ProgressionRules;
import com.tatakai.manager.service.rules.SheetRules;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CharacterSheetService {

    private final CharacterSheetRepository sheetRepository;
    private final CampaignMemberRepository memberRepository;
    private final CharacterSheetVisibilityRepository visibilityRepository;
    private final SheetActivityLogService activityLog;
    private final ApplicationEventPublisher events;
    private final CampaignNpcRepository campaignNpcRepository;
    private final NpcSheetVisibilityRepository npcVisibilityRepository;
    private final NpcRepository npcRepository;
    private final UserRepository userRepository;
    private final CampaignRuleService ruleService;
    private final CatalogService catalogService;
    private final CharacterBuildRepository buildRepository;

    /** Seções da ficha de NPC que o Mestre pode liberar aos jogadores. */
    private static final List<SheetSection> NPC_SHEET_SECTIONS = List.of(
            SheetSection.ATTRIBUTES, SheetSection.RESOURCES, SheetSection.INJURIES, SheetSection.INVENTORY);

    /** Quem age sobre a ficha: papel efetivo, nome (para eventos) e flags de acesso. */
    private record Access(UUID actorId, String actorName, ActorRole role,
                          boolean master, boolean owner, boolean privileged) {}

    public CharacterSheetService(CharacterSheetRepository sheetRepository,
                                 CampaignMemberRepository memberRepository,
                                 CharacterSheetVisibilityRepository visibilityRepository,
                                 SheetActivityLogService activityLog,
                                 ApplicationEventPublisher events,
                                 CampaignNpcRepository campaignNpcRepository,
                                 NpcSheetVisibilityRepository npcVisibilityRepository,
                                 NpcRepository npcRepository,
                                 UserRepository userRepository,
                                 CampaignRuleService ruleService,
                                 CatalogService catalogService,
                                 CharacterBuildRepository buildRepository) {
        this.sheetRepository = sheetRepository;
        this.memberRepository = memberRepository;
        this.visibilityRepository = visibilityRepository;
        this.activityLog = activityLog;
        this.events = events;
        this.campaignNpcRepository = campaignNpcRepository;
        this.npcVisibilityRepository = npcVisibilityRepository;
        this.npcRepository = npcRepository;
        this.userRepository = userRepository;
        this.ruleService = ruleService;
        this.catalogService = catalogService;
        this.buildRepository = buildRepository;
    }

    @Transactional
    public CharacterSheetResponse create(UUID campaignId, CreateCharacterSheetRequest req, UUID requesterId) {
        CampaignMember member = requireMembership(campaignId, requesterId);
        if (member.getRole() != Role.PLAYER) {
            throw new AccessDeniedException("Apenas jogadores têm ficha de personagem");
        }
        if (!ruleService.isEnabled(campaignId, OptionalRule.FREE_CREATION)) {
            throw new InvalidCharacterSheetException("Esta campanha usa a Construção Inicial");
        }
        if (sheetRepository.existsByCampaignMemberId(member.getId())) {
            throw new CharacterSheetAlreadyExistsException();
        }

        CharacterSheet sheet = CharacterSheet.builder()
                .campaignMember(member)
                .buildStatus(BuildStatus.DRAFT)
                .build();
        applyBuild(sheet, req.name(), req.bodyType(), req.dominance(), req.heightM(), req.weightKg(),
                req.attributes());
        CharacterSheet saved = sheetRepository.save(sheet);

        activityLog.record(saved, requesterId, ActorRole.PLAYER, ActivityCategory.BUILD, "CREATE_SHEET",
                null, null, saved.getName(), null, null);
        publish(SheetEvent.SHEET_CREATED, saved, ActivityCategory.BUILD, "CREATE_SHEET", member.getUser().getName());
        return toResponse(saved, true, Set.of());
    }

    @Transactional(readOnly = true)
    public List<CharacterSheetSummaryResponse> listForCampaign(UUID campaignId, UUID requesterId) {
        CampaignMember requester = requireMembership(campaignId, requesterId);
        boolean master = requester.getRole() == Role.MASTER;
        return sheetRepository.findByCampaignMemberCampaignId(campaignId).stream()
                .map(s -> {
                    boolean privileged = master || s.getCampaignMember().getUser().getId().equals(requesterId);
                    return toSummary(s, hiddenSections(s, privileged));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public CharacterSheetResponse get(UUID sheetId, UUID requesterId) {
        CharacterSheet sheet = loadSheet(sheetId);
        Access a = access(sheet, requesterId);
        return toResponse(sheet, a.privileged(), hiddenSections(sheet, a.privileged()));
    }

    @Transactional
    public CharacterSheetResponse update(UUID sheetId, UpdateCharacterSheetRequest req, UUID requesterId) {
        CharacterSheet sheet = loadSheet(sheetId);
        Access a = access(sheet, requesterId);
        requireOwner(a);
        requireNotInBuild(sheet);
        if (!sheet.isNpcSheet() && sheet.getBuildStatus() != BuildStatus.DRAFT) {
            throw new SheetLockedException("A ficha só pode ser editada enquanto estiver em rascunho");
        }
        applyBuild(sheet, req.name(), req.bodyType(), req.dominance(), req.heightM(), req.weightKg(),
                req.attributes());
        CharacterSheet saved = sheetRepository.save(sheet);

        activityLog.record(saved, requesterId, a.role(), ActivityCategory.BUILD, "UPDATE_SHEET",
                null, null, null, null, null);
        publish(SheetEvent.SHEET_UPDATED, saved, ActivityCategory.BUILD, "UPDATE_SHEET", a.actorName());
        return toResponse(saved, true, Set.of());
    }

    @Transactional
    public CharacterSheetResponse submit(UUID sheetId, UUID requesterId) {
        CharacterSheet sheet = loadSheet(sheetId);
        Access a = access(sheet, requesterId);
        requireNoNpcApproval(sheet);
        requireOwner(a);
        requireNotInBuild(sheet);
        if (sheet.getBuildStatus() != BuildStatus.DRAFT) {
            throw new SheetLockedException("Apenas fichas em rascunho podem ser enviadas para aprovação");
        }
        BuildStatus before = sheet.getBuildStatus();
        sheet.setBuildStatus(BuildStatus.PENDING_APPROVAL);
        CharacterSheet saved = sheetRepository.save(sheet);

        activityLog.record(saved, requesterId, ActorRole.PLAYER, ActivityCategory.BUILD, "SUBMIT_SHEET",
                "buildStatus", before, saved.getBuildStatus(), null, null);
        publish(SheetEvent.SHEET_UPDATED, saved, ActivityCategory.BUILD, "SUBMIT_SHEET", a.actorName());
        return toResponse(saved, true, Set.of());
    }

    @Transactional
    public CharacterSheetResponse approve(UUID sheetId, ApproveCharacterSheetRequest req, UUID requesterId) {
        CharacterSheet sheet = loadSheet(sheetId);
        Access a = access(sheet, requesterId);
        requireNoNpcApproval(sheet);
        requireMaster(a);
        if (sheet.getBuildStatus() != BuildStatus.PENDING_APPROVAL) {
            throw new SheetLockedException("A ficha não está aguardando aprovação");
        }
        BuildStatus before = sheet.getBuildStatus();
        boolean approved = Boolean.TRUE.equals(req.approved());
        // rejeição devolve à Construção Inicial quando a ficha nasceu dela
        BuildStatus rejected = buildRepository.existsById(sheet.getId()) ? BuildStatus.IN_BUILD : BuildStatus.DRAFT;
        sheet.setBuildStatus(approved ? BuildStatus.COMPLETED : rejected);
        CharacterSheet saved = sheetRepository.save(sheet);

        String action = approved ? "APPROVE_SHEET" : "REJECT_SHEET";
        activityLog.record(saved, requesterId, ActorRole.MASTER, ActivityCategory.BUILD, action,
                "buildStatus", before, saved.getBuildStatus(), null, req.reason());
        publish(SheetEvent.SHEET_UPDATED, saved, ActivityCategory.BUILD, action, a.actorName());
        return toResponse(saved, true, Set.of());
    }

    @Transactional
    public CharacterSheetResponse updateResources(UUID sheetId, UpdateResourcesRequest req, UUID requesterId) {
        CharacterSheet sheet = loadSheet(sheetId);
        Access a = access(sheet, requesterId);
        if (!a.master()) {
            requireOwner(a);
        }
        ActorRole role = a.role();

        int effMax = SheetRules.effectiveHpMax(sheet.getHpMax(), sheet.getInjuries());
        if (req.currentHp() != null) {
            int v = Math.min(req.currentHp(), effMax);
            logResource(sheet, requesterId, role, "HP", sheet.getCurrentHp(), v, req.reason());
            sheet.setCurrentHp(v);
        }
        if (req.currentSp() != null) {
            int v = Math.min(req.currentSp(), sheet.getSpMax());
            logResource(sheet, requesterId, role, "SP", sheet.getCurrentSp(), v, req.reason());
            sheet.setCurrentSp(v);
        }
        if (req.currentKp() != null) {
            int v = Math.min(req.currentKp(), sheet.getKpMax());
            logResource(sheet, requesterId, role, "KP", sheet.getCurrentKp(), v, req.reason());
            sheet.setCurrentKp(v);
        }
        if (req.physicalFatigue() != null) {
            logResource(sheet, requesterId, role, "physicalFatigue", sheet.getPhysicalFatigue(), req.physicalFatigue(), req.reason());
            sheet.setPhysicalFatigue(req.physicalFatigue());
        }
        if (req.mentalFatigue() != null) {
            logResource(sheet, requesterId, role, "mentalFatigue", sheet.getMentalFatigue(), req.mentalFatigue(), req.reason());
            sheet.setMentalFatigue(req.mentalFatigue());
        }
        CharacterSheet saved = sheetRepository.save(sheet);
        publish(SheetEvent.SHEET_UPDATED, saved, ActivityCategory.RESOURCE, "UPDATE_RESOURCES", a.actorName());
        return toResponse(saved, true, Set.of());
    }

    @Transactional
    public CharacterSheetResponse applyGmAdjustment(UUID sheetId, GmAdjustmentRequest req, UUID requesterId) {
        CharacterSheet sheet = loadSheet(sheetId);
        Access a = access(sheet, requesterId);
        requireMaster(a);

        Change c = applyAction(sheet, req);
        CharacterSheet saved = sheetRepository.save(sheet);

        String action = req.action().name();
        activityLog.record(saved, requesterId, ActorRole.MASTER, c.category, action, c.field,
                c.before, c.after, null, req.reason());
        publish(SheetEvent.SHEET_UPDATED, saved, c.category, action, a.actorName());
        return toResponse(saved, true, Set.of());
    }

    /** Reuso pelo fluxo de Construção Inicial (submit): mapeia a ficha já carregada para quem consulta. */
    public CharacterSheetResponse toResponseFor(CharacterSheet sheet, UUID requesterId) {
        Access a = access(sheet, requesterId);
        return toResponse(sheet, a.privileged(), hiddenSections(sheet, a.privileged()));
    }

    private void requireNotInBuild(CharacterSheet sheet) {
        if (sheet.getBuildStatus() == BuildStatus.IN_BUILD) {
            throw new SheetLockedException("A ficha está em Construção Inicial");
        }
    }

    // ---------------------------------------------------------------- ajustes do Mestre

    private record Change(ActivityCategory category, String field, Object before, Object after) {}

    private Change applyAction(CharacterSheet s, GmAdjustmentRequest req) {
        ActivityCategory gm = ActivityCategory.GM_ADJUSTMENT;
        switch (req.action()) {
            case GRANT_FIGHT_EXPERIENCE -> {
                int v = requireValue(req);
                if (v <= 0) throw new InvalidCharacterSheetException("O valor deve ser maior que zero");
                int before = s.getFightExperience();
                s.setFightExperience(before + v);
                return new Change(gm, "fightExperience", before, s.getFightExperience());
            }
            case ADJUST_OVERCOME_POINTS -> {
                int v = requireValue(req);
                int before = s.getPendingOvercomePoints();
                if (v == 0 || before + v < 0) {
                    throw new InvalidCharacterSheetException("Ajuste de pontos de superação inválido");
                }
                s.setPendingOvercomePoints(before + v);
                return new Change(gm, "pendingOvercomePoints", before, before + v);
            }
            case SET_RESOURCE -> {
                ResourceType r = parseEnum(ResourceType.class, req.field());
                int v = requireValue(req);
                if (v < 0) throw new InvalidCharacterSheetException("O valor não pode ser negativo");
                switch (r) {
                    case HP -> {
                        int before = s.getCurrentHp();
                        s.setCurrentHp(Math.min(v, SheetRules.effectiveHpMax(s.getHpMax(), s.getInjuries())));
                        return new Change(gm, "HP", before, s.getCurrentHp());
                    }
                    case SP -> {
                        int before = s.getCurrentSp();
                        s.setCurrentSp(Math.min(v, s.getSpMax()));
                        return new Change(gm, "SP", before, s.getCurrentSp());
                    }
                    default -> {
                        int before = s.getCurrentKp();
                        s.setCurrentKp(Math.min(v, s.getKpMax()));
                        return new Change(gm, "KP", before, s.getCurrentKp());
                    }
                }
            }
            case SET_MAX -> {
                ResourceType r = parseEnum(ResourceType.class, req.field());
                int v = requireValue(req);
                if (v < 1) throw new InvalidCharacterSheetException("O máximo deve ser pelo menos 1");
                switch (r) {
                    case HP -> {
                        int before = s.getHpMax();
                        boolean full = s.getCurrentHp() >= SheetRules.effectiveHpMax(before, s.getInjuries());
                        s.setHpMax(v);
                        int eff = SheetRules.effectiveHpMax(v, s.getInjuries());
                        s.setCurrentHp(full ? eff : Math.min(s.getCurrentHp(), eff));
                        return new Change(gm, "HP_MAX", before, v);
                    }
                    case SP -> {
                        int before = s.getSpMax();
                        boolean full = s.getCurrentSp() >= before;
                        s.setSpMax(v);
                        s.setCurrentSp(full ? v : Math.min(s.getCurrentSp(), v));
                        return new Change(gm, "SP_MAX", before, v);
                    }
                    default -> {
                        int before = s.getKpMax();
                        boolean full = s.getCurrentKp() >= before;
                        s.setKpMax(v);
                        s.setCurrentKp(full ? v : Math.min(s.getCurrentKp(), v));
                        return new Change(gm, "KP_MAX", before, v);
                    }
                }
            }
            case SET_ATTRIBUTE -> {
                Attribute a = parseEnum(Attribute.class, req.field());
                int v = requireValue(req);
                if (v < 0) throw new InvalidCharacterSheetException("O atributo não pode ser negativo");
                int before = s.getAttribute(a);
                s.setAttribute(a, v);
                return new Change(gm, a.name(), before, v);
            }
            case SET_OPEN_WOUNDS -> {
                int v = requireValue(req);
                if (v < 0) throw new InvalidCharacterSheetException("O valor não pode ser negativo");
                int before = s.getOpenWounds();
                s.setOpenWounds(v);
                return new Change(gm, "openWounds", before, v);
            }
            case ADD_INJURY -> {
                InjuryPayload p = req.injury();
                if (p == null) throw new InvalidCharacterSheetException("Informe os dados do ferimento");
                PermanentInjury injury = PermanentInjury.builder()
                        .sheet(s)
                        .type(p.type())
                        .region(p.region())
                        .severity(p.severity())
                        .hpMaxReduction(SheetRules.injuryHpReduction(p.severity(), p.type(), s.getHpMax(), p.damage()))
                        .limiterReduction(SheetRules.injuryLimiterReduction(p.severity()))
                        .affectedAttribute(p.affectedAttribute())
                        .build();
                s.getInjuries().add(injury);
                s.setCurrentHp(Math.min(s.getCurrentHp(), SheetRules.effectiveHpMax(s.getHpMax(), s.getInjuries())));
                return new Change(gm, "injury", null,
                        p.type() + "/" + p.severity() + "/" + p.region() + " (-" + injury.getHpMaxReduction() + " HP)");
            }
            case TREAT_INJURY -> {
                PermanentInjury injury = findInjury(s, req.targetId());
                injury.setTreated(true);
                return new Change(gm, "injury:" + injury.getId(), "untreated", "treated");
            }
            case REMOVE_INJURY -> {
                PermanentInjury injury = findInjury(s, req.targetId());
                s.getInjuries().remove(injury);
                return new Change(gm, "injury:" + injury.getId(),
                        injury.getType() + "/" + injury.getSeverity() + "/" + injury.getRegion(), null);
            }
            case ADJUST_FATIGUE -> {
                FatigueType t = parseEnum(FatigueType.class, req.field());
                int v = requireValue(req);
                int before = t == FatigueType.PHYSICAL ? s.getPhysicalFatigue() : s.getMentalFatigue();
                if (before + v < 0) throw new InvalidCharacterSheetException("O desgaste não pode ficar negativo");
                if (t == FatigueType.PHYSICAL) s.setPhysicalFatigue(before + v);
                else s.setMentalFatigue(before + v);
                return new Change(gm, t == FatigueType.PHYSICAL ? "physicalFatigue" : "mentalFatigue",
                        before, before + v);
            }
            case ADJUST_MONEY -> {
                int v = requireValue(req);
                int before = s.getMoney();
                if (before + v < 0) throw new InvalidCharacterSheetException("O dinheiro não pode ficar negativo");
                s.setMoney(before + v);
                return new Change(gm, "money", before, before + v);
            }
            case ADD_ITEM -> {
                ItemPayload p = requireItem(req);
                s.getInventory().add(InventoryItem.builder().sheet(s).name(p.name())
                        .quantity(p.quantity()).description(p.description()).weightKg(p.weight()).build());
                return new Change(ActivityCategory.INVENTORY, "item", null, p.name() + " x" + p.quantity());
            }
            case UPDATE_ITEM -> {
                ItemPayload p = requireItem(req);
                InventoryItem item = findItem(s, req.targetId());
                String before = item.getName() + " x" + item.getQuantity();
                item.setName(p.name());
                item.setQuantity(p.quantity());
                item.setDescription(p.description());
                item.setWeightKg(p.weight());
                return new Change(ActivityCategory.INVENTORY, "item:" + item.getId(), before,
                        p.name() + " x" + p.quantity());
            }
            case REMOVE_ITEM -> {
                InventoryItem item = findItem(s, req.targetId());
                s.getInventory().remove(item);
                return new Change(ActivityCategory.INVENTORY, "item:" + item.getId(),
                        item.getName() + " x" + item.getQuantity(), null);
            }
            case ADD_TRAIT -> {
                if (req.targetId() == null) throw new InvalidCharacterSheetException("Informe o traço");
                TraitDefinition t = catalogService.findTrait(req.targetId())
                        .orElseThrow(SheetEntryNotFoundException::new);
                // Ficha de NPC: vale o traço de qualquer campanha em que o NPC esteja associado.
                boolean inScope = t.getCampaignId() == null || (s.isNpcSheet()
                        ? campaignNpcRepository.existsByCampaignIdAndNpcId(t.getCampaignId(), s.getNpcId())
                        : t.getCampaignId().equals(campaignIdOf(s)));
                if (!inScope) {
                    throw new SheetEntryNotFoundException(); // fora do catálogo desta campanha
                }
                if (s.getTraits().stream().anyMatch(x -> t.getId().equals(x.getTraitId()))) {
                    throw new InvalidCharacterSheetException("A ficha já possui este traço");
                }
                // RN-TRAIT-01: depois da construção, só os efeitos valem (ganhos não são aplicados)
                s.getTraits().add(CharacterTrait.builder().sheet(s).traitId(t.getId())
                        .traitVersion(t.getVersion()).name(t.getName()).rarity(t.getRarity())
                        .category(t.getCategory()).description(t.getDescription()).buildGains(t.getBuildGains())
                        .acquiredIn(TraitAcquisition.CAMPAIGN).gainsApplied(false).build());
                return new Change(ActivityCategory.PROGRESSION, "trait", null, t.getName());
            }
            case REMOVE_TRAIT -> {
                if (req.targetId() == null) throw new InvalidCharacterSheetException("Informe o traço");
                CharacterTrait t = s.getTraits().stream().filter(x -> req.targetId().equals(x.getId()))
                        .findFirst().orElseThrow(SheetEntryNotFoundException::new);
                s.getTraits().remove(t);
                return new Change(ActivityCategory.PROGRESSION, "trait:" + t.getId(), t.getName(), null);
            }
            default -> throw new InvalidCharacterSheetException("Ação inválida");
        }
    }

    private int requireValue(GmAdjustmentRequest req) {
        if (req.value() == null) throw new InvalidCharacterSheetException("Informe o valor do ajuste");
        return req.value();
    }

    private ItemPayload requireItem(GmAdjustmentRequest req) {
        if (req.item() == null) throw new InvalidCharacterSheetException("Informe os dados do item");
        return req.item();
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String name) {
        if (name == null) throw new InvalidCharacterSheetException("Informe o campo do ajuste");
        try {
            return Enum.valueOf(type, name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidCharacterSheetException("Campo inválido: " + name);
        }
    }

    private PermanentInjury findInjury(CharacterSheet s, UUID id) {
        if (id == null) throw new InvalidCharacterSheetException("Informe o ferimento");
        return s.getInjuries().stream().filter(i -> id.equals(i.getId())).findFirst()
                .orElseThrow(SheetEntryNotFoundException::new);
    }

    private InventoryItem findItem(CharacterSheet s, UUID id) {
        if (id == null) throw new InvalidCharacterSheetException("Informe o item");
        return s.getInventory().stream().filter(i -> id.equals(i.getId())).findFirst()
                .orElseThrow(SheetEntryNotFoundException::new);
    }

    // ---------------------------------------------------------------- construção

    /** Copia os dados de construção e recalcula máximos; atual = máximo (ficha em rascunho). */
    private void applyBuild(CharacterSheet s, String name, BodyType bodyType,
                            com.tatakai.manager.entity.Dominance dominance, Double heightM,
                            Double weightKg, AttributesPayload a) {
        s.setName(name);
        s.setBodyType(bodyType);
        s.setDominance(dominance);
        s.setHeightM(heightM);
        s.setWeightKg(weightKg != null ? weightKg
                : Math.round(SheetRules.initialWeight(heightM, bodyType, a.con()) * 10) / 10.0);
        s.setStr(a.str());
        s.setDex(a.dex());
        s.setCon(a.con());
        s.setIntl(a.intl());
        s.setCha(a.cha());
        s.setMen(a.men());
        s.setTal(a.tal());
        s.setHpMax(SheetRules.initialHpMax(a.con()));
        s.setSpMax(SheetRules.initialSpMax(a.con()));
        s.setKpMax(SheetRules.initialKpMax(a.con()));
        s.setCurrentHp(s.getHpMax());
        s.setCurrentSp(s.getSpMax());
        s.setCurrentKp(s.getKpMax());
    }

    private void logResource(CharacterSheet sheet, UUID actorId, ActorRole role, String field,
                             int before, int after, String reason) {
        activityLog.record(sheet, actorId, role, ActivityCategory.RESOURCE, "UPDATE_RESOURCE",
                field, before, after, null, reason);
    }

    // ---------------------------------------------------------------- acesso e visibilidade

    private CharacterSheet loadSheet(UUID sheetId) {
        return sheetRepository.findById(sheetId).orElseThrow(CharacterSheetNotFoundException::new);
    }

    private UUID campaignIdOf(CharacterSheet sheet) {
        return sheet.getCampaignMember().getCampaign().getId();
    }

    private CampaignMember requireMembership(UUID campaignId, UUID userId) {
        return memberRepository.findByCampaignIdAndUserId(campaignId, userId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"));
    }

    /** Único ponto que resolve quem é o requester diante da ficha (jogador ou NPC). */
    private Access access(CharacterSheet sheet, UUID requesterId) {
        if (sheet.isNpcSheet()) {
            if (!requesterId.equals(sheet.getNpcOwnerId())) throw new CharacterSheetNotFoundException();
            return new Access(requesterId, userName(requesterId), ActorRole.MASTER, true, true, true);
        }
        CampaignMember m = requireMembership(campaignIdOf(sheet), requesterId);
        boolean master = m.getRole() == Role.MASTER;
        boolean owner = sheet.getCampaignMember().getUser().getId().equals(requesterId);
        return new Access(requesterId, m.getUser().getName(),
                master ? ActorRole.MASTER : ActorRole.PLAYER, master, owner, master || owner);
    }

    private String userName(UUID userId) {
        return userRepository.findById(userId).map(User::getName).orElse(null);
    }

    private void requireMaster(Access a) {
        if (!a.master()) {
            throw new AccessDeniedException("Apenas o Mestre pode realizar esta ação");
        }
    }

    private void requireOwner(Access a) {
        if (!a.owner()) {
            throw new AccessDeniedException("Apenas o dono da ficha pode realizar esta ação");
        }
    }

    private void requireNoNpcApproval(CharacterSheet sheet) {
        if (sheet.isNpcSheet()) {
            throw new InvalidCharacterSheetException("Ficha de NPC não passa por aprovação");
        }
    }

    /** Seções ocultas para quem consulta; Mestre e dono não têm nada oculto. */
    private Set<SheetSection> hiddenSections(CharacterSheet sheet, boolean privileged) {
        if (privileged || sheet.isNpcSheet()) return Set.of();
        Set<SheetSection> hidden = EnumSet.noneOf(SheetSection.class);
        for (CharacterSheetVisibility v : visibilityRepository.findByCampaignMemberId(sheet.getCampaignMember().getId())) {
            if (v.isHiddenByMaster() || v.isHiddenBySelf()) hidden.add(v.getSection());
        }
        return hidden;
    }

    private void publish(SheetEvent event, CharacterSheet sheet, ActivityCategory category,
                         String action, String actorName) {
        if (sheet.isNpcSheet()) {
            for (CampaignNpc cn : campaignNpcRepository.findByNpcId(sheet.getNpcId())) {
                events.publishEvent(new SheetUpdateMessage(event, cn.getCampaign().getId(), sheet.getId(),
                        category, action, actorName));
            }
            return;
        }
        events.publishEvent(new SheetUpdateMessage(event, campaignIdOf(sheet), sheet.getId(),
                category, action, actorName));
    }

    // ---------------------------------------------------------------- ficha de NPC

    @Transactional
    public CharacterSheetResponse createForNpc(UUID npcId, CreateCharacterSheetRequest req, UUID requesterId) {
        Npc npc = npcRepository.findById(npcId).orElseThrow(NpcNotFoundException::new);
        if (!requesterId.equals(npc.getOwnerId())) {
            throw new AccessDeniedException("Apenas o dono do NPC pode criar a ficha");
        }
        if (sheetRepository.existsByNpcId(npcId)) {
            throw new NpcSheetAlreadyExistsException();
        }
        CharacterSheet sheet = CharacterSheet.builder()
                .npcId(npcId)
                .npcOwnerId(npc.getOwnerId())
                .buildStatus(BuildStatus.COMPLETED)
                .build();
        applyBuild(sheet, req.name(), req.bodyType(), req.dominance(), req.heightM(), req.weightKg(),
                req.attributes());
        CharacterSheet saved = sheetRepository.save(sheet);

        activityLog.record(saved, requesterId, ActorRole.MASTER, ActivityCategory.BUILD, "CREATE_SHEET",
                null, null, saved.getName(), null, null);
        publish(SheetEvent.SHEET_CREATED, saved, ActivityCategory.BUILD, "CREATE_SHEET", userName(requesterId));
        return toResponse(saved, true, Set.of());
    }

    /**
     * Ficha instantânea do Mestre (§26.11): cria um NPC só com ficha (sem interações, fora da Agenda), associa à
     * campanha e cria a ficha COMPLETED. Visível = NPC visível e todas as seções liberadas; senão, tudo oculto.
     */
    @Transactional
    public CharacterSheetResponse createGmSheet(UUID campaignId, CreateGmSheetRequest req, UUID requesterId) {
        CampaignMember m = requireMembership(campaignId, requesterId);
        if (m.getRole() != Role.MASTER) throw new AccessDeniedException("Apenas o Mestre pode criar fichas instantâneas");
        Npc npc = npcRepository.save(Npc.builder()
                .id(UUID.randomUUID())
                .name(req.sheet().name())
                .ownerId(requesterId)
                .createdAt(java.time.Instant.now())
                .build());
        CampaignNpc cn = campaignNpcRepository.save(CampaignNpc.builder()
                .campaign(m.getCampaign()).npcId(npc.getId()).visible(req.visible()).bookable(false).build());
        if (req.visible()) {
            for (SheetSection sec : NPC_SHEET_SECTIONS) {
                npcVisibilityRepository.save(NpcSheetVisibility.builder().campaignNpc(cn).section(sec).visible(true).build());
            }
        }
        return createForNpc(npc.getId(), req.sheet(), requesterId);
    }

    @Transactional(readOnly = true)
    public CharacterSheetResponse getForNpc(UUID npcId, UUID requesterId) {
        CharacterSheet sheet = sheetRepository.findByNpcId(npcId).orElseThrow(CharacterSheetNotFoundException::new);
        access(sheet, requesterId); // 404 se não for o dono
        return toResponse(sheet, true, Set.of());
    }

    @Transactional(readOnly = true)
    public CharacterSheetResponse getNpcSheetInCampaign(UUID campaignId, UUID npcId, UUID requesterId) {
        CampaignNpc cn = campaignNpcRepository.findByCampaignIdAndNpcId(campaignId, npcId)
                .orElseThrow(NpcNotFoundException::new);
        CampaignMember member = requireMembership(campaignId, requesterId);
        boolean master = member.getRole() == Role.MASTER;
        if (!master && !cn.isVisible()) throw new NpcNotFoundException();
        CharacterSheet sheet = sheetRepository.findByNpcId(npcId).orElseThrow(CharacterSheetNotFoundException::new);
        if (master) return toResponse(sheet, true, Set.of());

        Set<SheetSection> hidden = EnumSet.copyOf(NPC_SHEET_SECTIONS);
        for (NpcSheetVisibility v : npcVisibilityRepository.findByCampaignNpcId(cn.getId())) {
            if (v.isVisible()) hidden.remove(v.getSection());
        }
        return toResponse(sheet, false, hidden);
    }

    @Transactional(readOnly = true)
    public List<NpcSectionVisibilityResponse> listNpcSheetVisibility(UUID campaignId, UUID npcId, UUID requesterId) {
        CampaignNpc cn = masterCampaignNpc(campaignId, npcId, requesterId).cn();
        Set<SheetSection> visible = EnumSet.noneOf(SheetSection.class);
        for (NpcSheetVisibility v : npcVisibilityRepository.findByCampaignNpcId(cn.getId())) {
            if (v.isVisible()) visible.add(v.getSection());
        }
        return NPC_SHEET_SECTIONS.stream()
                .map(sec -> new NpcSectionVisibilityResponse(sec, visible.contains(sec))).toList();
    }

    @Transactional
    public NpcSectionVisibilityResponse setNpcSheetVisibility(UUID campaignId, UUID npcId,
                                                              SetNpcSectionVisibilityRequest req, UUID requesterId) {
        var ctx = masterCampaignNpc(campaignId, npcId, requesterId);
        if (!NPC_SHEET_SECTIONS.contains(req.section())) {
            throw new InvalidCharacterSheetException("Seção inválida para ficha de NPC: " + req.section());
        }
        boolean visible = Boolean.TRUE.equals(req.visible());
        NpcSheetVisibility row = npcVisibilityRepository
                .findByCampaignNpcIdAndSection(ctx.cn().getId(), req.section())
                .orElseGet(() -> NpcSheetVisibility.builder()
                        .campaignNpc(ctx.cn()).section(req.section()).build());
        row.setVisible(visible);
        npcVisibilityRepository.save(row);

        sheetRepository.findByNpcId(npcId).ifPresent(sheet -> events.publishEvent(new SheetUpdateMessage(
                SheetEvent.SHEET_UPDATED, campaignId, sheet.getId(), ActivityCategory.GM_ADJUSTMENT,
                "SET_NPC_SHEET_VISIBILITY", ctx.member().getUser().getName())));
        return new NpcSectionVisibilityResponse(req.section(), visible);
    }

    private record MasterCtx(CampaignNpc cn, CampaignMember member) {}

    private MasterCtx masterCampaignNpc(UUID campaignId, UUID npcId, UUID requesterId) {
        CampaignMember member = requireMembership(campaignId, requesterId);
        if (member.getRole() != Role.MASTER) {
            throw new AccessDeniedException("Apenas o Mestre pode realizar esta ação");
        }
        CampaignNpc cn = campaignNpcRepository.findByCampaignIdAndNpcId(campaignId, npcId)
                .orElseThrow(NpcNotFoundException::new);
        return new MasterCtx(cn, member);
    }

    // ---------------------------------------------------------------- mapeamento

    private CharacterSheetResponse toResponse(CharacterSheet s, boolean privileged, Set<SheetSection> hidden) {
        boolean showAttr = !hidden.contains(SheetSection.ATTRIBUTES);
        boolean showRes = !hidden.contains(SheetSection.RESOURCES);
        boolean showInj = !hidden.contains(SheetSection.INJURIES);
        boolean showInv = !hidden.contains(SheetSection.INVENTORY);
        CampaignMember m = s.getCampaignMember(); // null em ficha de NPC
        boolean npc = s.isNpcSheet();
        int effMax = SheetRules.effectiveHpMax(s.getHpMax(), s.getInjuries());
        Map<String, Integer> adj = s.getLimiterAdjustments() == null ? Map.of() : s.getLimiterAdjustments();

        Map<Attribute, Integer> attributes = null;
        SheetDerived derived = null;
        if (showAttr) {
            attributes = new EnumMap<>(Attribute.class);
            Map<Attribute, Integer> mods = new EnumMap<>(Attribute.class);
            Map<Attribute, AttributeBand> bands = new EnumMap<>(Attribute.class);
            Map<Attribute, Integer> limiters = new EnumMap<>(Attribute.class);
            for (Attribute a : Attribute.values()) {
                int v = s.getAttribute(a);
                attributes.put(a, v);
                mods.put(a, SheetRules.mod(v));
                bands.put(a, SheetRules.band(v));
                if (a != Attribute.TAL) {
                    int red = SheetRules.injuryLimiter(s.getInjuries(), a);
                    limiters.put(a, SheetRules.attributeLimiter(a, s.getTal(), s.getBodyType(), red,
                            adj.getOrDefault(a.name(), 0)));
                }
            }
            derived = new SheetDerived(mods, bands, limiters,
                    SheetRules.statusLimiter(ResourceType.HP, s.getTal(), s.getBodyType(),
                            adj.getOrDefault("HP", 0) - SheetRules.injuryHp(s.getInjuries())),
                    SheetRules.statusLimiter(ResourceType.SP, s.getTal(), s.getBodyType(),
                            adj.getOrDefault("SP", 0)),
                    SheetRules.statusLimiter(ResourceType.KP, s.getTal(), s.getBodyType(),
                            adj.getOrDefault("KP", 0)),
                    SheetRules.weightMod(s.getWeightKg()), SheetRules.carryKg(s.getStr()),
                    criticalState(s, effMax),
                    SheetRules.carriedKg(s.getInventory()),
                    SheetRules.loadState(SheetRules.carriedKg(s.getInventory()), SheetRules.carryKg(s.getStr())));
        }

        SheetResources resources = null;
        SheetFatigue fatigue = null;
        SheetProgress progress = null;
        if (showRes) {
            resources = new SheetResources(s.getHpMax(), effMax, s.getSpMax(), s.getKpMax(),
                    s.getCurrentHp(), s.getCurrentSp(), s.getCurrentKp(), s.getOpenWounds());
            fatigue = new SheetFatigue(s.getPhysicalFatigue(), SheetRules.fatigueBand(s.getPhysicalFatigue()),
                    s.getMentalFatigue(), SheetRules.fatigueBand(s.getMentalFatigue()));
            int el = s.getFightExperience();
            progress = new SheetProgress(el, s.getPendingOvercomePoints(), ProgressionRules.keiken(el),
                    ProgressionRules.nextKeikenAt(el), s.getSparringExperience(), ProgressionRules.sparringCap(el),
                    s.getBarrierOvercomePoints(), ProgressionRules.instinctSlots(el));
        }

        List<InjuryResponse> injuries = showInj ? s.getInjuries().stream()
                .map(i -> new InjuryResponse(i.getId(), i.getType(), i.getRegion(), i.getSeverity(),
                        i.getHpMaxReduction(), i.getLimiterReduction(), i.getAffectedAttribute(),
                        i.isTreated(), i.getCreatedAt()))
                .toList() : null;
        List<InventoryItemResponse> inventory = showInv ? s.getInventory().stream()
                .map(i -> new InventoryItemResponse(i.getId(), i.getName(), i.getQuantity(), i.getDescription(), i.getWeightKg()))
                .toList() : null;

        // traços/clãs: ocultos quando a seção TRAITS está oculta; ficha de NPC só para o Mestre
        boolean showTraits = !hidden.contains(SheetSection.TRAITS) && (!npc || privileged);
        List<CharacterTraitResponse> traits = showTraits ? s.getTraits().stream()
                .map(t -> new CharacterTraitResponse(t.getId(), t.getTraitId(), t.getTraitVersion(), t.getName(),
                        t.getRarity(), t.getCategory(), t.getDescription(), t.getAcquiredIn(), t.isGainsApplied()))
                .toList() : null;
        List<CharacterClanResponse> clans = showTraits ? s.getClans().stream()
                .map(c -> new CharacterClanResponse(c.getId(), c.getClanId(), c.getName(),
                        catalogService.findClan(c.getClanId()).map(Clan::getHeritage).orElse(List.of())))
                .toList() : null;

        return new CharacterSheetResponse(s.getId(), npc ? null : m.getCampaign().getId(),
                npc ? s.getNpcOwnerId() : m.getUser().getId(),
                npc ? null : m.getUser().getName(), s.getName(), s.getBuildStatus(), s.getBodyType(), s.getDominance(),
                s.getHeightM(), s.getWeightKg(), attributes, derived, resources, fatigue, progress,
                showInv ? s.getMoney() : null, injuries, inventory,
                s.getVersion() == null ? 0L : s.getVersion(), s.getNpcId(), traits, clans);
    }

    private CharacterSheetSummaryResponse toSummary(CharacterSheet s, Set<SheetSection> hidden) {
        boolean showRes = !hidden.contains(SheetSection.RESOURCES);
        CampaignMember m = s.getCampaignMember();
        int effMax = SheetRules.effectiveHpMax(s.getHpMax(), s.getInjuries());
        return new CharacterSheetSummaryResponse(s.getId(), m.getUser().getId(), m.getUser().getName(),
                s.getName(), s.getBuildStatus(),
                showRes ? s.getCurrentHp() : null, showRes ? effMax : null,
                showRes ? s.getCurrentSp() : null, showRes ? s.getSpMax() : null,
                showRes ? s.getCurrentKp() : null, showRes ? s.getKpMax() : null,
                showRes ? criticalState(s, effMax) : null);
    }

    private boolean criticalState(CharacterSheet s, int effMax) {
        return SheetRules.criticalState(s.getCurrentHp(), effMax,
                s.getStr(), s.getDex(), s.getCon(), s.getIntl(), s.getCha(), s.getMen(), s.getTal());
    }
}
