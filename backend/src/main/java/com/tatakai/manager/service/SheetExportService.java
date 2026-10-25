package com.tatakai.manager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tatakai.manager.dto.response.SheetExportFile;
import com.tatakai.manager.dto.response.SheetExportFile.*;
import com.tatakai.manager.dto.response.SheetImportResponse;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.CharacterSheetAlreadyExistsException;
import com.tatakai.manager.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.exception.InvalidCharacterSheetException;
import com.tatakai.manager.repository.*;
import com.tatakai.manager.service.rules.SheetRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Exportar/importar ficha em JSON (F7, PLANO §21). O PDF é impressão do navegador, fora do backend. */
@Service
public class SheetExportService {

    static final int MAX_BYTES = 1024 * 1024;

    private final CharacterSheetRepository sheetRepository;
    private final CampaignMemberRepository memberRepository;
    private final CharacterSheetVisibilityRepository visibilityRepository;
    private final CharacterArtRepository artRepository;
    private final CharacterTechniqueRepository techniqueRepository;
    private final CharacterInstinctRepository instinctRepository;
    private final RivalRepository rivalRepository;
    private final UserRepository userRepository;
    private final TraitDefinitionRepository traitCatalog;
    private final ClanRepository clanCatalog;
    private final MartialArtRepository artCatalog;
    private final InstinctRepository instinctCatalog;
    private final SheetActivityLogService activityLog;
    private final CharacterSheetService sheetService;
    private final ObjectMapper mapper;

    public SheetExportService(CharacterSheetRepository sheetRepository,
                              CampaignMemberRepository memberRepository,
                              CharacterSheetVisibilityRepository visibilityRepository,
                              CharacterArtRepository artRepository,
                              CharacterTechniqueRepository techniqueRepository,
                              CharacterInstinctRepository instinctRepository,
                              RivalRepository rivalRepository,
                              UserRepository userRepository,
                              TraitDefinitionRepository traitCatalog,
                              ClanRepository clanCatalog,
                              MartialArtRepository artCatalog,
                              InstinctRepository instinctCatalog,
                              SheetActivityLogService activityLog,
                              CharacterSheetService sheetService,
                              ObjectMapper mapper) {
        this.sheetRepository = sheetRepository;
        this.memberRepository = memberRepository;
        this.visibilityRepository = visibilityRepository;
        this.artRepository = artRepository;
        this.techniqueRepository = techniqueRepository;
        this.instinctRepository = instinctRepository;
        this.rivalRepository = rivalRepository;
        this.userRepository = userRepository;
        this.traitCatalog = traitCatalog;
        this.clanCatalog = clanCatalog;
        this.artCatalog = artCatalog;
        this.instinctCatalog = instinctCatalog;
        this.activityLog = activityLog;
        this.sheetService = sheetService;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ exportar

    @Transactional
    public SheetExportFile export(UUID sheetId, UUID requesterId) {
        CharacterSheet s = sheetRepository.findById(sheetId).orElseThrow(CharacterSheetNotFoundException::new);
        Set<SheetSection> hidden = EnumSet.noneOf(SheetSection.class);
        ActorRole role = ActorRole.MASTER;
        String requesterName;
        if (s.isNpcSheet()) {
            if (!requesterId.equals(s.getNpcOwnerId())) throw new CharacterSheetNotFoundException();
            requesterName = userRepository.findById(requesterId).map(User::getName).orElse(null);
        } else {
            CampaignMember m = memberRepository
                    .findByCampaignIdAndUserId(s.getCampaignMember().getCampaign().getId(), requesterId)
                    .orElseThrow(CharacterSheetNotFoundException::new);
            boolean master = m.getRole() == Role.MASTER;
            if (!master && !s.getCampaignMember().getUser().getId().equals(requesterId)) {
                throw new CharacterSheetNotFoundException();
            }
            requesterName = m.getUser().getName();
            if (!master) {
                role = ActorRole.PLAYER;
                // RN-EXP-01: o jogador não leva o que o Mestre ocultou (nem o que ele mesmo ocultou: ignorado de propósito)
                for (CharacterSheetVisibility v : visibilityRepository.findByCampaignMemberId(s.getCampaignMember().getId())) {
                    if (v.isHiddenByMaster()) hidden.add(v.getSection());
                }
            }
        }
        SheetExportFile file = build(s, requesterId, requesterName, hidden);
        activityLog.record(s, requesterId, role, ActivityCategory.BUILD, "EXPORT", null, null, s.getName(), null, null);
        return file;
    }

    private SheetExportFile build(CharacterSheet s, UUID requesterId, String requesterName, Set<SheetSection> hidden) {
        boolean attr = !hidden.contains(SheetSection.ATTRIBUTES);
        boolean res = !hidden.contains(SheetSection.RESOURCES);
        boolean inj = !hidden.contains(SheetSection.INJURIES);
        boolean inv = !hidden.contains(SheetSection.INVENTORY);
        boolean traits = !hidden.contains(SheetSection.TRAITS);

        Map<Attribute, Integer> attributes = null;
        if (attr) {
            attributes = new EnumMap<>(Attribute.class);
            for (Attribute a : Attribute.values()) attributes.put(a, s.getAttribute(a));
        }
        SheetData data = new SheetData(s.getName(), s.getBodyType(), s.getDominance(), s.getHeightM(), s.getWeightKg(),
                attributes,
                res ? s.getHpMax() : null, res ? s.getSpMax() : null, res ? s.getKpMax() : null,
                res ? s.getCurrentHp() : null, res ? s.getCurrentSp() : null, res ? s.getCurrentKp() : null,
                res ? s.getOpenWounds() : null, res ? s.getPhysicalFatigue() : null, res ? s.getMentalFatigue() : null,
                res ? s.getFightExperience() : null, res ? s.getSparringExperience() : null,
                res ? s.getPendingOvercomePoints() : null, res ? s.getBarrierOvercomePoints() : null,
                res ? s.getLimiterAdjustments() : null, res ? s.getLimiterCarry() : null,
                inv ? s.getMoney() : null,
                inv ? s.getInventory().stream()
                        .map(i -> new ItemData(i.getName(), i.getQuantity(), i.getDescription(), i.getWeightKg())).toList() : null,
                inj ? s.getInjuries().stream()
                        .map(i -> new InjuryData(i.getType(), i.getRegion(), i.getSeverity(), i.getHpMaxReduction(),
                                i.getLimiterReduction(), i.getAffectedAttribute(), i.isTreated())).toList() : null);

        List<CharacterTechnique> techs = techniqueRepository.findBySheetIdOrderByCreatedAt(s.getId());
        List<ArtData> arts = artRepository.findBySheetIdOrderByCreatedAt(s.getId()).stream()
                .map(a -> new ArtData(a.getArtId(), a.getArtName(), a.getCategory(), a.getGraduation(), a.getStatus(),
                        a.getGraduationPoints(),
                        techs.stream().filter(t -> a.getId().equals(t.artIdOrNull())).map(this::techData).toList()))
                .toList();
        List<TechniqueData> loose = techs.stream().filter(t -> t.artIdOrNull() == null).map(this::techData).toList();

        return new SheetExportFile(SheetExportFile.SCHEMA_VERSION, Instant.now(),
                new ExportedBy(requesterId, requesterName),
                new Source(s.isNpcSheet() ? null : s.getCampaignMember().getCampaign().getId(), s.getId(), s.getNpcId()),
                data,
                traits ? s.getTraits().stream().map(t -> new TraitData(t.getTraitId(), t.getTraitVersion(), t.getName(),
                        t.getRarity(), t.getCategory(), t.getDescription(), t.getBuildGains(), t.getAcquiredIn(),
                        t.isGainsApplied())).toList() : null,
                traits ? s.getClans().stream().map(c -> new ClanData(c.getClanId(), c.getClanVersion(), c.getName(),
                        c.getBuildGains())).toList() : null,
                arts, loose,
                instinctRepository.findBySheetIdOrderByCreatedAt(s.getId()).stream()
                        .map(i -> new InstinctData(i.getInstinctId(), i.getName(), i.getRank(), i.getDescription())).toList(),
                rivalRepository.findBySheetIdOrderByName(s.getId()).stream()
                        .map(r -> new RivalData(r.getName(), r.getArt(), r.getFights(), r.getReadBonus(),
                                r.getKnownTechniques())).toList());
    }

    private TechniqueData techData(CharacterTechnique t) {
        return new TechniqueData(t.getKind(), t.getName(), t.getGraduation(), t.getComplexity(), t.getTrainingPoints(),
                t.getTrainingProgress(), t.getState(), t.getMastery(), t.getMasteryPoints(), t.getBonuses(),
                t.getBorrowedFrom());
    }

    // ------------------------------------------------------------------ importar

    @Transactional
    public SheetImportResponse importSheet(UUID campaignId, byte[] bytes, String filename, UUID requesterId) {
        CampaignMember member = memberRepository.findByCampaignIdAndUserId(campaignId, requesterId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"));
        if (member.getRole() != Role.PLAYER) {
            throw new AccessDeniedException("Apenas jogadores têm ficha de personagem");
        }
        if (sheetRepository.existsByCampaignMemberId(member.getId())) {
            throw new CharacterSheetAlreadyExistsException();
        }
        SheetExportFile f = parse(bytes, filename);
        List<String> warnings = new ArrayList<>();
        SheetData d = f.sheet();

        CharacterSheet s = CharacterSheet.builder().campaignMember(member)
                .buildStatus(BuildStatus.PENDING_APPROVAL).build();
        s.setName(d.name());
        s.setBodyType(d.bodyType() != null ? d.bodyType() : BodyType.MESOMORPH);
        s.setDominance(d.dominance() != null ? d.dominance() : Dominance.RIGHT);
        s.setHeightM(d.heightM() != null ? d.heightM() : 1.7);
        for (Attribute a : Attribute.values()) {
            Integer v = d.attributes() == null ? null : d.attributes().get(a);
            int clamped = v == null ? 0 : Math.max(0, Math.min(999, v));
            if (v != null && v != clamped) warnings.add("Atributo " + a + " fora de 0..999 (" + v + "): ajustado para " + clamped);
            s.setAttribute(a, clamped);
        }
        s.setWeightKg(d.weightKg() != null ? d.weightKg()
                : Math.round(SheetRules.initialWeight(s.getHeightM(), s.getBodyType(), s.getCon()) * 10) / 10.0);
        s.setHpMax(orDefault(d.hpMax(), SheetRules.initialHpMax(s.getCon())));
        s.setSpMax(orDefault(d.spMax(), SheetRules.initialSpMax(s.getCon())));
        s.setKpMax(orDefault(d.kpMax(), SheetRules.initialKpMax(s.getCon())));
        s.setOpenWounds(nonNeg(d.openWounds()));
        s.setPhysicalFatigue(nonNeg(d.physicalFatigue()));
        s.setMentalFatigue(nonNeg(d.mentalFatigue()));
        s.setFightExperience(nonNeg(d.fightExperience()));
        s.setSparringExperience(nonNeg(d.sparringExperience()));
        s.setPendingOvercomePoints(nonNeg(d.pendingOvercomePoints()));
        s.setBarrierOvercomePoints(nonNeg(d.barrierOvercomePoints()));
        if (d.limiterAdjustments() != null) s.setLimiterAdjustments(new HashMap<>(d.limiterAdjustments()));
        if (d.limiterCarry() != null) s.setLimiterCarry(new HashMap<>(d.limiterCarry()));
        s.setMoney(nonNeg(d.money()));
        if (d.inventory() != null) {
            for (ItemData i : d.inventory()) {
                s.getInventory().add(InventoryItem.builder().sheet(s).name(i.name())
                        .quantity(Math.max(i.quantity(), 0)).description(i.description())
                        .weightKg(i.weightKg() == null ? 0 : Math.max(0, i.weightKg())).build());
            }
        }
        if (d.injuries() != null) {
            for (InjuryData i : d.injuries()) {
                s.getInjuries().add(PermanentInjury.builder().sheet(s).type(i.type()).region(i.region())
                        .severity(i.severity()).hpMaxReduction(i.hpMaxReduction())
                        .limiterReduction(i.limiterReduction()).affectedAttribute(i.affectedAttribute())
                        .treated(i.treated()).build());
            }
        }
        // recursos atuais: ausentes = cheios; acima do máximo = cortados (aviso)
        s.setCurrentHp(clampCurrent("HP", d.currentHp(), SheetRules.effectiveHpMax(s.getHpMax(), s.getInjuries()), warnings));
        s.setCurrentSp(clampCurrent("SP", d.currentSp(), s.getSpMax(), warnings));
        s.setCurrentKp(clampCurrent("KP", d.currentKp(), s.getKpMax(), warnings));

        if (f.traits() != null) {
            for (TraitData t : f.traits()) {
                var found = byIdOrName(traitCatalog::findById, traitCatalog::findFirstByName, t.traitId(), t.name());
                if (found.isEmpty()) {
                    warnings.add("Traço \"" + t.name() + "\" não existe no catálogo");
                } else if (found.get().getVersion() != t.traitVersion()) {
                    warnings.add("Traço \"" + t.name() + "\" tem versão diferente no catálogo (mantido o snapshot do arquivo)");
                }
                s.getTraits().add(CharacterTrait.builder().sheet(s)
                        .traitId(found.map(TraitDefinition::getId).orElse(t.traitId())).traitVersion(t.traitVersion())
                        .name(t.name()).rarity(t.rarity()).category(t.category()).description(t.description())
                        .buildGains(t.buildGains()).acquiredIn(t.acquiredIn()).gainsApplied(t.gainsApplied()).build());
            }
        }
        if (f.clans() != null) {
            for (ClanData c : f.clans()) {
                var found = byIdOrName(clanCatalog::findById, clanCatalog::findFirstByName, c.clanId(), c.name());
                if (found.isEmpty()) warnings.add("Clã \"" + c.name() + "\" não existe no catálogo");
                else if (found.get().getVersion() != c.clanVersion()) {
                    warnings.add("Clã \"" + c.name() + "\" tem versão diferente no catálogo (mantido o snapshot do arquivo)");
                }
                s.getClans().add(CharacterClan.builder().sheet(s).clanId(found.map(Clan::getId).orElse(c.clanId())).clanVersion(c.clanVersion())
                        .name(c.name()).buildGains(c.buildGains()).build());
            }
        }
        CharacterSheet saved = sheetRepository.save(s);

        if (f.arts() != null) {
            for (ArtData a : f.arts()) {
                var found = byIdOrName(artCatalog::findById, artCatalog::findFirstByName, a.artId(), a.artName());
                if (found.isEmpty()) warnings.add("Arte \"" + a.artName() + "\" não existe no catálogo");
                CharacterArt art = artRepository.save(CharacterArt.builder().sheet(saved)
                        .artId(found.map(MartialArt::getId).orElse(a.artId()))
                        .artName(a.artName()).category(a.category()).graduation(a.graduation()).status(a.status())
                        .graduationPoints(Math.max(a.graduationPoints(), 0)).build());
                if (a.techniques() != null) for (TechniqueData t : a.techniques()) saveTechnique(saved, art, t);
            }
        }
        if (f.looseTechniques() != null) for (TechniqueData t : f.looseTechniques()) saveTechnique(saved, null, t);
        if (f.instincts() != null) {
            for (InstinctData i : f.instincts()) {
                var found = byIdOrName(instinctCatalog::findById, instinctCatalog::findFirstByName, i.instinctId(), i.name());
                if (found.isEmpty()) warnings.add("Instinto \"" + i.name() + "\" não existe no catálogo");
                instinctRepository.save(CharacterInstinct.builder().sheet(saved)
                        .instinctId(found.map(Instinct::getId).orElse(i.instinctId()))
                        .name(i.name()).rank(i.rank()).description(i.description()).build());
            }
        }
        if (f.rivals() != null) {
            for (RivalData r : f.rivals()) {
                rivalRepository.save(Rival.builder().sheet(saved).name(r.name()).art(r.art()).fights(r.fights())
                        .readBonus(Math.min(Math.max(r.readBonus(), 0), Rival.MAX_READ))
                        .knownTechniques(r.knownTechniques() == null ? new ArrayList<>() : new ArrayList<>(r.knownTechniques()))
                        .build());
            }
        }
        activityLog.record(saved, requesterId, ActorRole.PLAYER, ActivityCategory.BUILD, "IMPORT",
                null, null, filename, null, null);
        return new SheetImportResponse(sheetService.toResponseFor(saved, requesterId), warnings);
    }

    private void saveTechnique(CharacterSheet sheet, CharacterArt art, TechniqueData t) {
        techniqueRepository.save(CharacterTechnique.builder().sheet(sheet).characterArt(art)
                .kind(t.kind() != null ? t.kind() : MoveKind.TECHNIQUE).name(t.name()).graduation(t.graduation())
                .complexity(t.complexity()).trainingPoints(t.trainingPoints()).trainingProgress(t.trainingProgress())
                .state(t.state() != null ? t.state() : TechniqueState.REQUESTED)
                .mastery(t.mastery() != null ? t.mastery() : Mastery.SKETCH).masteryPoints(t.masteryPoints())
                .bonuses(t.bonuses() == null ? new ArrayList<>() : new ArrayList<>(t.bonuses()))
                .borrowedFrom(t.borrowedFrom()).build());
    }

    private SheetExportFile parse(byte[] bytes, String filename) {
        if (bytes == null || bytes.length == 0) throw new InvalidCharacterSheetException("Arquivo vazio");
        if (bytes.length > MAX_BYTES) throw new InvalidCharacterSheetException("O arquivo excede 1 MB");
        if (filename == null || !filename.toLowerCase().endsWith(".json")) {
            throw new InvalidCharacterSheetException("Envie um arquivo .json");
        }
        SheetExportFile f;
        try {
            f = mapper.readValue(bytes, SheetExportFile.class);
        } catch (Exception e) {
            throw new InvalidCharacterSheetException("Arquivo de ficha inválido");
        }
        if (f.schemaVersion() != SheetExportFile.SCHEMA_VERSION) {
            throw new InvalidCharacterSheetException("Versão de arquivo não suportada: " + f.schemaVersion());
        }
        if (f.sheet() == null || f.sheet().name() == null || f.sheet().name().isBlank()) {
            throw new InvalidCharacterSheetException("Arquivo sem dados da ficha");
        }
        return f;
    }

    private static int orDefault(Integer v, int def) {
        return v == null ? def : Math.max(v, 0);
    }

    private static int nonNeg(Integer v) {
        return v == null ? 0 : Math.max(v, 0);
    }

    private static int clampCurrent(String label, Integer v, int max, List<String> warnings) {
        if (v == null) return max;
        if (v > max) {
            warnings.add(label + " atual (" + v + ") acima do máximo (" + max + "): ajustado");
            return max;
        }
        return Math.max(v, 0);
    }

    /** Procura no catálogo pelo id do arquivo e, se não achar, pelo nome. */
    private static <T> java.util.Optional<T> byIdOrName(java.util.function.Function<UUID, java.util.Optional<T>> byId,
                                                         java.util.function.Function<String, java.util.Optional<T>> byName,
                                                         UUID id, String name) {
        java.util.Optional<T> found = id == null ? java.util.Optional.empty() : byId.apply(id);
        return found.isPresent() || name == null ? found : byName.apply(name);
    }
}
