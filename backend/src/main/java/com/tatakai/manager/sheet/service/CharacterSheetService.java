package com.tatakai.manager.sheet.service;

import com.tatakai.manager.sheet.entity.ItemLocation;
import com.tatakai.manager.sheet.service.rules.InventoryRules;
import com.tatakai.manager.sheet.dto.request.MoveItemRequest;
import com.tatakai.manager.sheet.dto.request.AddWoundRequest;
import com.tatakai.manager.sheet.entity.WoundMark;
import com.tatakai.manager.sheet.dto.response.WoundMarkResponse;
import com.tatakai.manager.sheet.service.rules.WoundRules;
import com.tatakai.manager.sheet.service.rules.DoseRules;
import com.tatakai.manager.sheet.entity.DoseEntry;
import com.tatakai.manager.roll.service.RollService;
import com.tatakai.manager.sheet.entity.BodyPart;
import com.tatakai.manager.sheet.dto.response.SheetUpdateMessage.SheetEvent;

import com.tatakai.manager.auth.entity.User;
import com.tatakai.manager.auth.repository.UserRepository;
import com.tatakai.manager.build.entity.BuildStatus;
import com.tatakai.manager.build.repository.CharacterBuildRepository;
import com.tatakai.manager.campaign.dto.response.CampaignUpdateMessage;
import com.tatakai.manager.campaign.entity.CampaignMember;
import com.tatakai.manager.campaign.entity.OptionalRule;
import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.campaign.service.CampaignRuleService;
import com.tatakai.manager.catalog.entity.Clan;
import com.tatakai.manager.catalog.entity.TraitAcquisition;
import com.tatakai.manager.catalog.entity.TraitDefinition;
import com.tatakai.manager.catalog.service.CatalogService;
import com.tatakai.manager.catalog.service.ConsumableCatalog;
import com.tatakai.manager.catalog.service.EquipmentCatalog;
import com.tatakai.manager.catalog.service.WeaponCatalog;
import com.tatakai.manager.npc.entity.CampaignNpc;
import com.tatakai.manager.npc.entity.Npc;
import com.tatakai.manager.npc.exception.NpcNotFoundException;
import com.tatakai.manager.npc.repository.CampaignNpcRepository;
import com.tatakai.manager.npc.repository.NpcRepository;
import com.tatakai.manager.progression.dto.response.SheetProgress;
import com.tatakai.manager.progression.service.rules.ProgressionRules;
import com.tatakai.manager.shared.exception.AccessDeniedException;
import com.tatakai.manager.sheet.dto.request.ApproveCharacterSheetRequest;
import com.tatakai.manager.sheet.dto.request.AttributesPayload;
import com.tatakai.manager.sheet.dto.request.CreateCharacterSheetRequest;
import com.tatakai.manager.sheet.dto.request.CreateGmSheetRequest;
import com.tatakai.manager.sheet.dto.request.GmAdjustmentRequest;
import com.tatakai.manager.sheet.dto.request.InjuryPayload;
import com.tatakai.manager.sheet.dto.request.ItemPayload;
import com.tatakai.manager.sheet.dto.request.SetNpcSectionVisibilityRequest;
import com.tatakai.manager.sheet.dto.request.UpdateCharacterSheetRequest;
import com.tatakai.manager.sheet.dto.request.UpdateResourcesRequest;
import com.tatakai.manager.sheet.dto.response.CharacterClanResponse;
import com.tatakai.manager.sheet.dto.response.CharacterSheetResponse;
import com.tatakai.manager.sheet.dto.response.CharacterSheetSummaryResponse;
import com.tatakai.manager.sheet.dto.response.CharacterTraitResponse;
import com.tatakai.manager.sheet.dto.response.InjuryResponse;
import com.tatakai.manager.sheet.dto.response.InventoryItemResponse;
import com.tatakai.manager.sheet.dto.response.NpcSectionVisibilityResponse;
import com.tatakai.manager.sheet.dto.response.SheetDerived;
import com.tatakai.manager.sheet.dto.response.SheetFatigue;
import com.tatakai.manager.sheet.dto.response.SheetResources;
import com.tatakai.manager.sheet.dto.response.SheetUpdateMessage;
import com.tatakai.manager.sheet.entity.ActivityCategory;
import com.tatakai.manager.sheet.entity.ActorRole;
import com.tatakai.manager.sheet.entity.Attribute;
import com.tatakai.manager.sheet.entity.AttributeBand;
import com.tatakai.manager.sheet.entity.BodyType;
import com.tatakai.manager.sheet.entity.CharacterSheet;
import com.tatakai.manager.sheet.entity.CharacterSheetVisibility;
import com.tatakai.manager.sheet.entity.CharacterTrait;
import com.tatakai.manager.sheet.entity.Dominance;
import com.tatakai.manager.sheet.entity.FatigueType;
import com.tatakai.manager.sheet.entity.InventoryItem;
import com.tatakai.manager.sheet.entity.NpcSheetVisibility;
import com.tatakai.manager.sheet.entity.PermanentInjury;
import com.tatakai.manager.sheet.entity.ResourceType;
import com.tatakai.manager.sheet.entity.SheetSection;
import com.tatakai.manager.sheet.service.rules.NpcVisibilityRules;
import com.tatakai.manager.npc.dto.request.NpcInteractionDto;
import com.tatakai.manager.sheet.dto.request.UpdateNpcSheetVisibilityRequest;
import com.tatakai.manager.sheet.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.sheet.exception.InvalidCharacterSheetException;
import com.tatakai.manager.sheet.exception.NpcSheetAlreadyExistsException;
import com.tatakai.manager.sheet.exception.SheetEntryNotFoundException;
import com.tatakai.manager.sheet.exception.SheetLockedException;
import com.tatakai.manager.sheet.repository.CharacterSheetRepository;
import com.tatakai.manager.sheet.repository.CharacterSheetVisibilityRepository;
import com.tatakai.manager.sheet.repository.NpcSheetVisibilityRepository;
import com.tatakai.manager.sheet.service.rules.SheetRules;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tatakai.manager.sheet.entity.SheetImageKind;
import com.tatakai.manager.sheet.repository.SheetImageRepository;

import java.util.ArrayList;
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
    private static final List<SheetSection> NPC_SHEET_SECTIONS = SheetSection.NPC_SECTIONS;

    /** Quem age sobre a ficha: papel efetivo, nome (para eventos) e flags de acesso. */
    private record Access(UUID actorId, String actorName, ActorRole role,
                          boolean master, boolean owner, boolean privileged) {}

    /** Catálogo estático (só leitura do classpath): instanciado aqui para não mexer no construtor. */
    private final SheetImageRepository imageRepository;
    private final RollService rollService;
    private final WeaponCatalog weaponCatalog = new WeaponCatalog();
    private final EquipmentCatalog equipmentCatalog = new EquipmentCatalog();
    private final ConsumableCatalog consumableCatalog = new ConsumableCatalog();

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
                                 CharacterBuildRepository buildRepository,
                                 SheetImageRepository imageRepository,
                                 RollService rollService) {
        this.rollService = rollService;
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
        this.imageRepository = imageRepository;
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
        List<CharacterSheet> sheets = sheetRepository.findByCampaignMemberCampaignId(campaignId);
        Map<UUID, Set<SheetImageKind>> images = imageKinds(sheets.stream().map(CharacterSheet::getId).toList());
        return sheets.stream()
                .map(s -> {
                    boolean privileged = master || s.getCampaignMember().getUser().getId().equals(requesterId);
                    return toSummary(s, hiddenSections(s, privileged),
                            images.getOrDefault(s.getId(), Set.of()));
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
        SheetRules.requireCombatReady(sheet);
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

    // ---------------------------------------------------------------- inventário: mover e trocar itens

    /** Dono ou Mestre movem um item (equipar, guardar, pôr num contêiner) ou trocam dois de lugar, sem aprovação. */
    @Transactional
    public CharacterSheetResponse moveItem(UUID sheetId, UUID itemId, MoveItemRequest req, UUID requesterId) {
        CharacterSheet s = loadSheet(sheetId);
        Access a = access(s, requesterId);
        if (!a.privileged()) throw new AccessDeniedException("Apenas o dono da ficha ou o Mestre move itens");
        List<InventoryItem> inv = s.getInventory();
        InventoryItem item = findItem(s, itemId);
        InventoryItem other = req.swapWithId() == null ? null : findItem(s, req.swapWithId());
        if (other == item) throw new InvalidCharacterSheetException("Escolha outro item para trocar");

        ItemLocation fromLoc = item.getLocation();
        UUID fromBox = InventoryRules.areaOf(item, inv, this::containerOf);
        ItemLocation toLoc = req.location();
        UUID toBox = toLoc == ItemLocation.QUICK || toLoc == ItemLocation.PACK ? req.containerId() : null;
        String before = locationText(fromLoc, fromBox, inv);

        place(item, toLoc, toBox);
        if (other != null) place(other, fromLoc, fromBox);
        InventoryRules.requireEmptyIfUnworn(inv, item, this::containerOf);
        if (other != null) InventoryRules.requireEmptyIfUnworn(inv, other, this::containerOf);
        InventoryRules.check(inv, toLoc, toBox, this::containerOf, this::handsOf);
        if (other != null) InventoryRules.check(inv, fromLoc, fromBox, this::containerOf, this::handsOf);

        CharacterSheet saved = sheetRepository.save(s);
        String after = locationText(toLoc, toBox, inv) + (other != null ? " (trocou com " + other.getName() + ")" : "");
        activityLog.record(saved, requesterId, a.role(), ActivityCategory.INVENTORY, "MOVE_ITEM",
                "item:" + item.getId(), item.getName() + ": " + before, after, null, null);
        publish(SheetEvent.SHEET_UPDATED, saved, ActivityCategory.INVENTORY, "MOVE_ITEM", a.actorName());
        return toResponse(saved, true, Set.of());
    }

    private static void place(InventoryItem i, ItemLocation loc, UUID box) {
        i.setLocation(loc);
        i.setContainerId(box);
        if (loc != ItemLocation.HAND) i.setTwoHanded(false);
    }

    private EquipmentCatalog.Container containerOf(InventoryItem i) {
        return equipmentCatalog.find(i.getEquipmentKey()).map(EquipmentCatalog.Def::container).orElse(null);
    }

    private int handsOf(InventoryItem i) {
        return weaponCatalog.find(i.getWeaponKey())
                .map(w -> w.forcesTwoHands() || (w.variable() && i.isTwoHanded()) ? 2 : 1).orElse(1);
    }

    private static String locationText(ItemLocation loc, UUID box, List<InventoryItem> inv) {
        String name = switch (loc) {
            case HAND -> "Em mãos"; case QUICK -> "Acesso Rápido"; case PACK -> "Carregando";
            case WORN -> "Vestido"; case DROPPED -> "No chão";
        };
        if (box == null) return name;
        return name + " · " + inv.stream().filter(i -> box.equals(i.getId())).map(InventoryItem::getName).findFirst().orElse("?");
    }

    // ---------------------------------------------------------------- ferimentos desenhados (§53.3)

    /** Mestre desenha uma marca no boneco; com {@code injury}, cria também o Ferimento Permanente na mesma parte. */
    @Transactional
    public CharacterSheetResponse addWound(UUID sheetId, AddWoundRequest req, UUID requesterId) {
        CharacterSheet sheet = loadSheet(sheetId);
        Access a = access(sheet, requesterId);
        requireMaster(a);
        WoundRules.validate(req.type(), req.geometry());
        sheet.getWounds().add(WoundMark.builder().sheet(sheet).part(req.part()).type(req.type())
                .geometry(req.geometry()).severity(req.severity()).build());
        if (req.injury() != null) addInjury(sheet, req.injury(), req.part());
        CharacterSheet saved = sheetRepository.save(sheet);
        activityLog.record(saved, requesterId, ActorRole.MASTER, ActivityCategory.GM_ADJUSTMENT, "ADD_WOUND",
                "wound:" + req.part(), null, req.type() + (req.injury() != null ? " + ferimento" : ""), null, null);
        publish(SheetEvent.SHEET_UPDATED, saved, ActivityCategory.GM_ADJUSTMENT, "ADD_WOUND", a.actorName());
        return toResponse(saved, true, Set.of());
    }

    @Transactional
    public CharacterSheetResponse removeWound(UUID sheetId, UUID woundId, UUID requesterId) {
        CharacterSheet sheet = loadSheet(sheetId);
        Access a = access(sheet, requesterId);
        requireMaster(a);
        WoundMark w = sheet.getWounds().stream().filter(x -> woundId.equals(x.getId())).findFirst()
                .orElseThrow(SheetEntryNotFoundException::new);
        sheet.getWounds().remove(w);
        CharacterSheet saved = sheetRepository.save(sheet);
        activityLog.record(saved, requesterId, ActorRole.MASTER, ActivityCategory.GM_ADJUSTMENT, "REMOVE_WOUND",
                "wound:" + w.getPart(), w.getType(), null, null, null);
        publish(SheetEvent.SHEET_UPDATED, saved, ActivityCategory.GM_ADJUSTMENT, "REMOVE_WOUND", a.actorName());
        return toResponse(saved, true, Set.of());
    }

    /**
     * Usa um consumível do inventário fora de combate (§54): Doses (Analgésico, Calmante, Pomada, Termogênico,
     * Energético) e Kit de Cutman (`option` HP = 2d6 HP, WOUNDS = fecha as Feridas). Dono ou Mestre.
     */
    @Transactional
    public CharacterSheetResponse useConsumable(UUID sheetId, UUID itemId, String option, UUID requesterId) {
        CharacterSheet s = loadSheet(sheetId);
        SheetRules.requireCombatReady(s);
        Access a = access(s, requesterId);
        if (!a.master()) requireOwner(a);
        InventoryItem item = findItem(s, itemId);
        String key = item.getConsumableKey() != null ? item.getConsumableKey() : item.getEquipmentKey();
        if (key == null || !DoseRules.SHEET_USE.contains(key)) {
            throw new InvalidCharacterSheetException(item.getName() + " não tem uso pela ficha");
        }
        if (item.getQuantity() <= 0) throw new InvalidCharacterSheetException(item.getName() + " acabou");
        List<DoseEntry> doses = s.getDoses() == null ? List.of() : s.getDoses();
        int factor = DoseRules.factor(doses, key);
        String result;
        switch (key) {
            case DoseRules.ANALGESICO -> {
                int m = Math.min(2, s.getPhysicalFatigue());
                s.setPhysicalFatigue(s.getPhysicalFatigue() - m);
                doses = DoseRules.take(doses, key, m);
                result = "ignora " + m + " de Desgaste Físico até o Repouso";
            }
            case DoseRules.POMADA -> {
                if (s.getPhysicalFatigue() <= 0) throw new InvalidCharacterSheetException("Sem Desgaste Físico para tratar");
                s.setPhysicalFatigue(s.getPhysicalFatigue() - 1);
                doses = DoseRules.take(doses, key, 0);
                result = "−1 Desgaste Físico";
            }
            case DoseRules.TERMOGENICO -> {
                s.setMentalFatigue(s.getMentalFatigue() + factor);
                doses = DoseRules.take(doses, key, 0);
                result = "+" + factor + " Desgaste Mental; desvantagem em Iniciativa até o Repouso";
            }
            case DoseRules.KIT_CUTMAN -> {
                if ("WOUNDS".equals(option)) {
                    if (s.getOpenWounds() <= 0) throw new InvalidCharacterSheetException("Não há Feridas abertas");
                    s.setOpenWounds(0);
                    result = "Feridas fechadas";
                } else {
                    int hp = rollService.rollInternal(s, "2d6", "Kit de Cutman: HP", requesterId, a.role()).getTotal();
                    int effMax = SheetRules.effectiveHpMax(s.getHpMax(), s.getInjuries());
                    s.setCurrentHp(Math.min(effMax, s.getCurrentHp() + hp));
                    result = "+" + hp + " HP";
                }
            }
            default -> { // CALMANTE, ENERGETICO: efeito no Repouso / no próximo combate
                doses = DoseRules.take(doses, key, 0);
                result = DoseRules.CALMANTE.equals(key) ? "o próximo Repouso remove +1 Desgaste Mental"
                        : "+1 Iniciativa e +1 Adrenalina no próximo combate";
            }
        }
        if (DoseRules.DOSES.contains(key) && factor > 1) result += " (dose repetida: Colateral dobrado)";
        s.setDoses(new ArrayList<>(doses));
        item.setQuantity(item.getQuantity() - 1);
        if (item.getQuantity() <= 0) s.getInventory().remove(item);
        CharacterSheet saved = sheetRepository.save(s);
        activityLog.record(saved, requesterId, a.role(), ActivityCategory.INVENTORY, "USE_CONSUMABLE",
                "item:" + item.getId(), item.getName(), result, null, null);
        publish(SheetEvent.SHEET_UPDATED, saved, ActivityCategory.INVENTORY, "USE_CONSUMABLE", a.actorName());
        return toResponse(saved, true, Set.of());
    }

    private PermanentInjury addInjury(CharacterSheet s, InjuryPayload p, BodyPart part) {
        PermanentInjury injury = PermanentInjury.builder()
                .sheet(s)
                .type(p.type())
                .region(p.region())
                .bodyPart(part)
                .severity(p.severity())
                .hpMaxReduction(SheetRules.injuryHpReduction(p.severity(), p.type(), s.getHpMax(), p.damage()))
                .limiterReduction(SheetRules.injuryLimiterReduction(p.severity()))
                .affectedAttribute(p.affectedAttribute())
                .build();
        s.getInjuries().add(injury);
        s.setCurrentHp(Math.min(s.getCurrentHp(), SheetRules.effectiveHpMax(s.getHpMax(), s.getInjuries())));
        return injury;
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
                PermanentInjury injury = addInjury(s, p, p.bodyPart());
                return new Change(gm, "injury", null,
                        p.type() + "/" + p.severity() + "/" + p.region() + " (-" + injury.getHpMaxReduction() + " HP)");
            }
            case SET_MISSING_PART -> {
                if (req.bodyPart() == null) throw new InvalidCharacterSheetException("Informe a parte do corpo");
                if (s.getMissingParts() == null) s.setMissingParts(new java.util.ArrayList<>());
                boolean before = s.getMissingParts().contains(req.bodyPart());
                boolean after = Boolean.TRUE.equals(req.missing());
                if (after && !before) s.getMissingParts().add(req.bodyPart());
                if (!after) s.getMissingParts().remove(req.bodyPart());
                return new Change(gm, "missingPart:" + req.bodyPart(), before, after);
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
                InventoryItem added = InventoryItem.builder().sheet(s).name(p.name())
                        .quantity(p.quantity()).description(p.description()).weightKg(p.weight()).build();
                applyKeys(added, p);
                s.getInventory().add(added);
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
                applyKeys(item, p);
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

    /** Liga o item ao catálogo de armas/equipamentos/consumíveis (chaves validadas) e define onde ele está. */
    private void applyKeys(InventoryItem item, ItemPayload p) {
        if (p.weaponKey() != null && !p.weaponKey().isBlank() && weaponCatalog.find(p.weaponKey()).isEmpty()) {
            throw new InvalidCharacterSheetException("Arma fora do catálogo: " + p.weaponKey());
        }
        if (p.equipmentKey() != null && !p.equipmentKey().isBlank() && equipmentCatalog.find(p.equipmentKey()).isEmpty()) {
            throw new InvalidCharacterSheetException("Equipamento fora do catálogo: " + p.equipmentKey());
        }
        if (p.consumableKey() != null && !p.consumableKey().isBlank() && consumableCatalog.find(p.consumableKey()).isEmpty()) {
            throw new InvalidCharacterSheetException("Consumível fora do catálogo: " + p.consumableKey());
        }
        item.setWeaponKey(p.weaponKey() == null || p.weaponKey().isBlank() ? null : p.weaponKey());
        item.setEquipmentKey(p.equipmentKey() == null || p.equipmentKey().isBlank() ? null : p.equipmentKey());
        item.setConsumableKey(p.consumableKey() == null || p.consumableKey().isBlank() ? null : p.consumableKey());
        if (p.location() != null) item.setLocation(p.location());
        weaponCatalog.find(item.getWeaponKey()).ifPresent(w -> {
            if (w.capacity() != null && item.getLoaded() == 0) item.setLoaded(w.capacity()); // chega carregada
        });
    }

    private InventoryItem findItem(CharacterSheet s, UUID id) {
        if (id == null) throw new InvalidCharacterSheetException("Informe o item");
        return s.getInventory().stream().filter(i -> id.equals(i.getId())).findFirst()
                .orElseThrow(SheetEntryNotFoundException::new);
    }

    // ---------------------------------------------------------------- construção

    /** Copia os dados de construção e recalcula máximos; atual = máximo (ficha em rascunho). */
    private void applyBuild(CharacterSheet s, String name, BodyType bodyType,
                            com.tatakai.manager.sheet.entity.Dominance dominance, Double heightM,
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
        // upsert (§51.8): a ficha leve criada com o NPC é completada aqui; ficha já pronta continua dando 409
        CharacterSheet sheet = sheetRepository.findByNpcId(npcId).orElseGet(() -> CharacterSheet.builder()
                .npcId(npcId)
                .npcOwnerId(npc.getOwnerId())
                .buildStatus(BuildStatus.COMPLETED)
                .build());
        if (sheet.getId() != null && sheet.isCombatReady()) {
            throw new NpcSheetAlreadyExistsException();
        }
        sheet.setCombatReady(true);
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
        events.publishEvent(new com.tatakai.manager.campaign.dto.response.CampaignUpdateMessage(campaignId, "NPCS", npc.getId()));
        return createForNpc(npc.getId(), req.sheet(), requesterId);
    }

    @Transactional(readOnly = true)
    public CharacterSheetResponse getForNpc(UUID npcId, UUID requesterId) {
        CharacterSheet sheet = sheetRepository.findByNpcId(npcId).orElseThrow(CharacterSheetNotFoundException::new);
        access(sheet, requesterId); // 404 se não for o dono
        return toResponse(sheet, true, Set.of());
    }

    /** Liga/desliga Cansaço e PS na ficha de NPC; só o dono do NPC (outros 404). */
    @Transactional
    public CharacterSheetResponse setNpcTracking(UUID npcId, boolean enabled, UUID requesterId) {
        CharacterSheet sheet = sheetRepository.findByNpcId(npcId).orElseThrow(CharacterSheetNotFoundException::new);
        Access a = access(sheet, requesterId); // 404 se não for o dono
        sheet.setNpcTracking(enabled);
        CharacterSheet saved = sheetRepository.save(sheet);
        publish(SheetEvent.SHEET_UPDATED, saved, ActivityCategory.GM_ADJUSTMENT, "NPC_TRACKING", a.actorName());
        return toResponse(saved, true, Set.of());
    }

    @Transactional(readOnly = true)
    public CharacterSheetResponse getNpcSheetInCampaign(UUID campaignId, UUID npcId, UUID requesterId) {
        CampaignNpc cn = campaignNpcRepository.findByCampaignIdAndNpcId(campaignId, npcId)
                .orElseThrow(NpcNotFoundException::new);
        CampaignMember member = requireMembership(campaignId, requesterId);
        boolean master = member.getRole() == Role.MASTER;
        if (!master && !cn.isVisible()) throw new NpcNotFoundException();
        CharacterSheet sheet = sheetRepository.findByNpcId(npcId).orElseThrow(CharacterSheetNotFoundException::new);
        Set<SheetSection> visible = master ? EnumSet.copyOf(SheetSection.NPC_SECTIONS)
                : NpcVisibilityRules.visible(npcVisibilityRepository.findByCampaignNpcId(cn.getId()));
        CharacterSheetResponse base = master ? toResponse(sheet, true, Set.of())
                : toResponse(sheet, false, NpcVisibilityRules.hidden(visible));
        List<NpcInteractionDto> interactions = null;
        if (visible.contains(SheetSection.INTERACTIONS)) {
            interactions = npcRepository.findById(npcId).map(n -> n.getInteractions().stream()
                    .map(i -> new NpcInteractionDto(i.getType(), i.getName(), i.getDescription(), i.getIdlePointCost()))
                    .toList()).orElse(List.of());
        }
        List<SheetSection> ordered = SheetSection.NPC_SECTIONS.stream().filter(visible::contains).toList();
        return base.withNpcView(interactions, ordered);
    }

    @Transactional(readOnly = true)
    public List<NpcSectionVisibilityResponse> listNpcSheetVisibility(UUID campaignId, UUID npcId, UUID requesterId) {
        CampaignNpc cn = masterCampaignNpc(campaignId, npcId, requesterId).cn();
        return npcVisibilityList(cn);
    }

    private List<NpcSectionVisibilityResponse> npcVisibilityList(CampaignNpc cn) {
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

    @Transactional
    public List<NpcSectionVisibilityResponse> updateNpcSheetVisibility(UUID campaignId, UUID npcId,
                                                                       UpdateNpcSheetVisibilityRequest req, UUID requesterId) {
        var ctx = masterCampaignNpc(campaignId, npcId, requesterId);
        for (SheetSection sec : req.sections().keySet()) {
            if (!NPC_SHEET_SECTIONS.contains(sec)) {
                throw new InvalidCharacterSheetException("Seção inválida para ficha de NPC: " + sec);
            }
        }
        req.sections().forEach((sec, visible) -> {
            NpcSheetVisibility row = npcVisibilityRepository.findByCampaignNpcIdAndSection(ctx.cn().getId(), sec)
                    .orElseGet(() -> NpcSheetVisibility.builder().campaignNpc(ctx.cn()).section(sec).build());
            row.setVisible(Boolean.TRUE.equals(visible));
            npcVisibilityRepository.save(row);
        });
        events.publishEvent(new com.tatakai.manager.campaign.dto.response.CampaignUpdateMessage(campaignId, "NPCS", npcId));
        return npcVisibilityList(ctx.cn());
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
        // ficha leve (§51.8): só os atributos crus (pré-preenchimento); nada derivado nem recursos
        boolean resOff = hidden.contains(SheetSection.RESOURCES) || !s.isCombatReady();
        boolean showHp = !resOff && !hidden.contains(SheetSection.HP);
        boolean showSp = !resOff && !hidden.contains(SheetSection.SP);
        boolean showKp = !resOff && !hidden.contains(SheetSection.KP);
        boolean npcOff = s.isNpcSheet() && !s.isNpcTracking(); // NPC sem Cansaço/PS (§53.2-6)
        boolean showFatigue = !resOff && !npcOff && !hidden.contains(SheetSection.FATIGUE);
        boolean showRadar = !hidden.contains(SheetSection.RADAR);
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
                if (!s.isCombatReady()) continue;
                mods.put(a, SheetRules.mod(v));
                bands.put(a, SheetRules.band(v));
                if (a != Attribute.TAL) {
                    int red = SheetRules.injuryLimiter(s.getInjuries(), a);
                    if (showRadar) limiters.put(a, SheetRules.attributeLimiter(a, s.getTal(), s.getBodyType(), red,
                            adj.getOrDefault(a.name(), 0)));
                }
            }
            if (s.isCombatReady()) derived = new SheetDerived(mods, bands, showRadar ? limiters : null,
                    !showRadar || !showHp ? null : SheetRules.statusLimiter(ResourceType.HP, s.getTal(), s.getBodyType(),
                            adj.getOrDefault("HP", 0) - SheetRules.injuryHp(s.getInjuries())),
                    !showRadar || !showSp ? null : SheetRules.statusLimiter(ResourceType.SP, s.getTal(), s.getBodyType(),
                            adj.getOrDefault("SP", 0) - SheetRules.injurySpLimiter(s.getInjuries())),
                    !showRadar || !showKp ? null : SheetRules.statusLimiter(ResourceType.KP, s.getTal(), s.getBodyType(),
                            adj.getOrDefault("KP", 0)),
                    SheetRules.weightMod(s.getWeightKg()), SheetRules.carryKg(s.getStr()),
                    showHp ? criticalState(s, effMax) : null,
                    SheetRules.carriedKg(s.getInventory()),
                    SheetRules.loadState(SheetRules.carriedKg(s.getInventory()), SheetRules.carryKg(s.getStr())));
        }

        SheetResources resources = null;
        SheetFatigue fatigue = null;
        SheetProgress progress = null;
        if (showHp || showSp || showKp) {
            resources = new SheetResources(showHp ? s.getHpMax() : null, showHp ? effMax : null,
                    showSp ? s.getSpMax() : null, showKp ? s.getKpMax() : null,
                    showHp ? s.getCurrentHp() : null, showSp ? s.getCurrentSp() : null,
                    showKp ? s.getCurrentKp() : null, showHp ? s.getOpenWounds() : null);
        }
        if (showFatigue) {
            fatigue = new SheetFatigue(s.getPhysicalFatigue(), SheetRules.fatigueBand(s.getPhysicalFatigue()),
                    s.getMentalFatigue(), SheetRules.fatigueBand(s.getMentalFatigue()),
                    s.getDoses() == null ? List.of() : List.copyOf(s.getDoses()));
        }
        if (showHp && showSp && showKp && !npcOff) {
            int el = s.getFightExperience();
            progress = new SheetProgress(el, s.getPendingOvercomePoints(), ProgressionRules.keiken(el),
                    ProgressionRules.nextKeikenAt(el), s.getSparringExperience(), ProgressionRules.sparringCap(el),
                    s.getBarrierOvercomePoints(), ProgressionRules.instinctSlots(el));
        }

        List<InjuryResponse> injuries = showInj ? s.getInjuries().stream()
                .map(i -> new InjuryResponse(i.getId(), i.getType(), i.getRegion(), i.getSeverity(),
                        i.getHpMaxReduction(), i.getLimiterReduction(), i.getAffectedAttribute(),
                        i.isTreated(), i.getCreatedAt(), i.getBodyPart()))
                .toList() : null;
        List<InventoryItemResponse> inventory = showInv ? s.getInventory().stream()
                .map(InventoryItemResponse::of)
                .toList() : null;

        // traços/clãs: ocultos quando a seção TRAITS está oculta; ficha de NPC só para o Mestre
        boolean showTraits = !hidden.contains(SheetSection.TRAITS); // NPC: o Mestre libera a seção (§51.8)
        List<CharacterTraitResponse> traits = showTraits ? s.getTraits().stream()
                .map(t -> new CharacterTraitResponse(t.getId(), t.getTraitId(), t.getTraitVersion(), t.getName(),
                        t.getRarity(), t.getCategory(), t.getDescription(), t.getAcquiredIn(), t.isGainsApplied(),
                        t.isGainsApplied() ? t.getBuildGains() : null))
                .toList() : null;
        boolean showClans = npc ? !hidden.contains(SheetSection.CLANS) : showTraits;
        List<CharacterClanResponse> clans = showClans ? s.getClans().stream()
                .map(c -> new CharacterClanResponse(c.getId(), c.getClanId(), c.getName(),
                        catalogService.findClan(c.getClanId()).map(Clan::getHeritage).orElse(List.of()), c.getBuildGains()))
                .toList() : null;

        Set<SheetImageKind> imgs = s.getId() == null ? Set.of()
                : imageKinds(List.of(s.getId())).getOrDefault(s.getId(), Set.of());
        return new CharacterSheetResponse(s.getId(), npc ? null : m.getCampaign().getId(),
                npc ? s.getNpcOwnerId() : m.getUser().getId(),
                npc ? null : m.getUser().getName(), s.getName(), s.getBuildStatus(), s.getBodyType(), s.getDominance(),
                s.getHeightM(), s.getWeightKg(), attributes, derived, resources, fatigue, progress,
                showInv ? s.getMoney() : null, injuries, inventory,
                s.getVersion() == null ? 0L : s.getVersion(), s.getNpcId(), traits, clans, s.isCombatReady(),
                imgs.contains(SheetImageKind.PORTRAIT), imgs.contains(SheetImageKind.FULL_BODY),
                showInj && !hidden.contains(SheetSection.BODY_MAP)
                        ? List.copyOf(s.getMissingParts() == null ? List.of() : s.getMissingParts()) : null,
                null, null, s.isNpcTracking(),
                showInj && !hidden.contains(SheetSection.BODY_MAP)
                        ? s.getWounds().stream().map(WoundMarkResponse::of).toList() : null);
    }

    /** Tipos de imagem por ficha, numa consulta só e sem carregar os bytes. */
    private Map<UUID, Set<SheetImageKind>> imageKinds(java.util.Collection<UUID> sheetIds) {
        Map<UUID, Set<SheetImageKind>> out = new java.util.HashMap<>();
        if (sheetIds.isEmpty()) return out;
        for (SheetImageRepository.Key k : imageRepository.findKeys(sheetIds)) {
            out.computeIfAbsent(k.sheetId(), x -> EnumSet.noneOf(SheetImageKind.class)).add(k.kind());
        }
        return out;
    }

    private CharacterSheetSummaryResponse toSummary(CharacterSheet s, Set<SheetSection> hidden,
                                                    Set<SheetImageKind> images) {
        boolean showRes = !hidden.contains(SheetSection.RESOURCES);
        CampaignMember m = s.getCampaignMember();
        int effMax = SheetRules.effectiveHpMax(s.getHpMax(), s.getInjuries());
        return new CharacterSheetSummaryResponse(s.getId(), m.getUser().getId(), m.getUser().getName(),
                s.getName(), s.getBuildStatus(),
                showRes ? s.getCurrentHp() : null, showRes ? effMax : null,
                showRes ? s.getCurrentSp() : null, showRes ? s.getSpMax() : null,
                showRes ? s.getCurrentKp() : null, showRes ? s.getKpMax() : null,
                showRes ? criticalState(s, effMax) : null,
                images.contains(SheetImageKind.PORTRAIT), images.contains(SheetImageKind.FULL_BODY));
    }

    private boolean criticalState(CharacterSheet s, int effMax) {
        return SheetRules.criticalState(s.getCurrentHp(), effMax,
                s.getStr(), s.getDex(), s.getCon(), s.getIntl(), s.getCha(), s.getMen(), s.getTal());
    }
}
