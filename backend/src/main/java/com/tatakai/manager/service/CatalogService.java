package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.CatalogImportRequest;
import com.tatakai.manager.dto.request.ClanPayload;
import com.tatakai.manager.dto.request.FightMilestonePayload;
import com.tatakai.manager.dto.request.InstinctPayload;
import com.tatakai.manager.dto.response.FightMilestoneResponse;
import com.tatakai.manager.dto.response.InstinctResponse;
import com.tatakai.manager.entity.FightMilestone;
import com.tatakai.manager.entity.Instinct;
import com.tatakai.manager.repository.FightMilestoneRepository;
import com.tatakai.manager.repository.InstinctRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tatakai.manager.dto.request.AssemblyCheckRequest;
import com.tatakai.manager.dto.request.MartialArtPayload;
import com.tatakai.manager.dto.request.ReviewArtRequest;
import com.tatakai.manager.dto.response.AssemblyCheck;
import com.tatakai.manager.dto.response.AssemblyModule;
import com.tatakai.manager.entity.ArtCategory;
import com.tatakai.manager.entity.ArtStatus;
import com.tatakai.manager.exception.InvalidMartialArtException;
import com.tatakai.manager.service.rules.AssemblyRules;
import org.springframework.core.io.ClassPathResource;
import com.tatakai.manager.dto.request.TraitPayload;
import com.tatakai.manager.dto.response.CatalogImportResponse;
import com.tatakai.manager.dto.response.CatalogPermissionsResponse;
import com.tatakai.manager.dto.response.ClanResponse;
import com.tatakai.manager.dto.response.MartialArtResponse;
import com.tatakai.manager.dto.response.MartialArtSummaryResponse;
import com.tatakai.manager.dto.response.TraitDefinitionResponse;
import com.tatakai.manager.entity.Clan;
import com.tatakai.manager.entity.MartialArt;
import com.tatakai.manager.entity.ArtBase;
import com.tatakai.manager.entity.ArtTechnique;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.entity.TraitDefinition;
import com.tatakai.manager.entity.TraitRarity;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.SheetEntryNotFoundException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.ClanRepository;
import com.tatakai.manager.repository.MartialArtRepository;
import com.tatakai.manager.repository.TraitDefinitionRepository;
import com.tatakai.manager.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Catálogo no Mongo: sem @Transactional (não cobre o Mongo). */
@Service
public class CatalogService {

    private final TraitDefinitionRepository traitRepository;
    private final ClanRepository clanRepository;
    private final CampaignMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final Set<String> editors;
    private final MartialArtRepository artRepository;
    private final InstinctRepository instinctRepository;
    private final FightMilestoneRepository milestoneRepository;
    private List<AssemblyModule> modules;

    public CatalogService(TraitDefinitionRepository traitRepository,
                          ClanRepository clanRepository,
                          CampaignMemberRepository memberRepository,
                          UserRepository userRepository,
                          @Value("${app.catalog.editors:}") String editors,
                          MartialArtRepository artRepository,
                          InstinctRepository instinctRepository,
                          FightMilestoneRepository milestoneRepository) {
        this.artRepository = artRepository;
        this.instinctRepository = instinctRepository;
        this.milestoneRepository = milestoneRepository;
        this.traitRepository = traitRepository;
        this.clanRepository = clanRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.editors = Arrays.stream(editors.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    public CatalogPermissionsResponse permissions(UUID requesterId) {
        return new CatalogPermissionsResponse(isGlobalEditor(requesterId));
    }

    // ---------- traços ----------

    public List<TraitDefinitionResponse> listTraits(UUID campaignIdOrNull, UUID requesterId) {
        List<TraitDefinition> list;
        if (campaignIdOrNull == null) {
            list = traitRepository.findByCampaignIdIsNull();
        } else {
            requireMember(campaignIdOrNull, requesterId);
            list = traitsForCampaign(campaignIdOrNull);
        }
        Collator collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
        collator.setStrength(Collator.PRIMARY);
        return list.stream()
                .sorted(Comparator.comparing(TraitDefinition::getRarity, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TraitDefinition::getName, Comparator.nullsLast(collator)))
                .map(CatalogService::toResponse).toList();
    }

    public TraitDefinitionResponse createTrait(UUID campaignIdOrNull, TraitPayload p, UUID requesterId) {
        requireWrite(campaignIdOrNull, requesterId);
        TraitDefinition t = new TraitDefinition();
        t.setId(UUID.randomUUID());
        t.setCampaignId(campaignIdOrNull);
        t.setVersion(0);
        apply(t, p);
        return toResponse(traitRepository.save(t));
    }

    public TraitDefinitionResponse updateTrait(UUID id, TraitPayload p, UUID requesterId) {
        TraitDefinition t = traitRepository.findById(id).orElseThrow(SheetEntryNotFoundException::new);
        requireWrite(t.getCampaignId(), requesterId);
        apply(t, p);
        return toResponse(traitRepository.save(t));
    }

    public void deleteTrait(UUID id, UUID requesterId) {
        TraitDefinition t = traitRepository.findById(id).orElseThrow(SheetEntryNotFoundException::new);
        requireWrite(t.getCampaignId(), requesterId);
        traitRepository.delete(t);
    }

    // ---------- clãs ----------

    public List<ClanResponse> listClans(UUID campaignIdOrNull, UUID requesterId) {
        List<Clan> list;
        if (campaignIdOrNull == null) {
            list = clanRepository.findByCampaignIdIsNull();
        } else {
            requireMember(campaignIdOrNull, requesterId);
            list = clansForCampaign(campaignIdOrNull);
        }
        return list.stream().map(CatalogService::toResponse).toList();
    }

    public ClanResponse createClan(UUID campaignIdOrNull, ClanPayload p, UUID requesterId) {
        requireWrite(campaignIdOrNull, requesterId);
        Clan c = new Clan();
        c.setId(UUID.randomUUID());
        c.setCampaignId(campaignIdOrNull);
        c.setVersion(0);
        apply(c, p);
        return toResponse(clanRepository.save(c));
    }

    public ClanResponse updateClan(UUID id, ClanPayload p, UUID requesterId) {
        Clan c = clanRepository.findById(id).orElseThrow(SheetEntryNotFoundException::new);
        requireWrite(c.getCampaignId(), requesterId);
        apply(c, p);
        return toResponse(clanRepository.save(c));
    }

    public void deleteClan(UUID id, UUID requesterId) {
        Clan c = clanRepository.findById(id).orElseThrow(SheetEntryNotFoundException::new);
        requireWrite(c.getCampaignId(), requesterId);
        clanRepository.delete(c);
    }

    // ---------- artes marciais ----------

    public List<MartialArtSummaryResponse> listArts(UUID campaignIdOrNull, UUID requesterId) {
        List<MartialArt> list;
        if (campaignIdOrNull == null) {
            list = artRepository.findByCampaignIdIsNull();
        } else {
            requireMember(campaignIdOrNull, requesterId);
            List<MartialArt> own = artRepository.findByCampaignId(campaignIdOrNull).stream()
                    .filter(a -> visible(a, requesterId)).toList();
            Set<String> ownNames = own.stream().filter(CatalogService::approved).map(MartialArt::getName)
                    .collect(Collectors.toSet());
            list = new ArrayList<>(own);
            for (MartialArt g : artRepository.findByCampaignIdIsNull()) {
                if (!ownNames.contains(g.getName())) list.add(g);
            }
        }
        return list.stream()
                .sorted(Comparator.comparing(MartialArt::getName, String.CASE_INSENSITIVE_ORDER))
                .map(CatalogService::toSummary).toList();
    }

    public MartialArtResponse getArt(UUID id, UUID requesterId) {
        MartialArt a = artRepository.findById(id).orElseThrow(SheetEntryNotFoundException::new);
        if (a.getCampaignId() != null) requireMember(a.getCampaignId(), requesterId);
        if (!visible(a, requesterId)) throw new SheetEntryNotFoundException();
        return toResponse(a);
    }

    public void deleteArt(UUID id, UUID requesterId) {
        MartialArt a = artRepository.findById(id).orElseThrow(SheetEntryNotFoundException::new);
        if (!ownDraft(a, requesterId)) requireWrite(a.getCampaignId(), requesterId);
        artRepository.delete(a);
    }

    // ---------- Criação Marcial (F3a) ----------

    /** Global: editor, aprovada. Campanha: Mestre cria aprovada; jogador cria Arte Criada em rascunho. */
    public MartialArtResponse createArt(UUID campaignIdOrNull, MartialArtPayload p, UUID requesterId) {
        boolean asPlayer = false;
        if (campaignIdOrNull == null) {
            requireWrite(null, requesterId);
        } else {
            requireMember(campaignIdOrNull, requesterId);
            asPlayer = !isMaster(campaignIdOrNull, requesterId);
        }
        MartialArt a = new MartialArt();
        a.setId(UUID.randomUUID());
        a.setCampaignId(campaignIdOrNull);
        a.setVersion(0);
        apply(a, p);
        a.setStatus(asPlayer ? ArtStatus.DRAFT : ArtStatus.APPROVED);
        if (asPlayer) {
            a.setAuthorId(requesterId);
            a.setCategory(ArtCategory.CREATED);
        }
        return toResponse(artRepository.save(a));
    }

    public MartialArtResponse updateArt(UUID id, MartialArtPayload p, UUID requesterId) {
        MartialArt a = artRepository.findById(id).orElseThrow(SheetEntryNotFoundException::new);
        if (!ownDraft(a, requesterId)) requireWrite(a.getCampaignId(), requesterId);
        apply(a, p);
        if (a.getAuthorId() != null) a.setCategory(ArtCategory.CREATED);
        return toResponse(artRepository.save(a));
    }

    public MartialArtResponse submitArt(UUID id, UUID requesterId) {
        MartialArt a = artRepository.findById(id).orElseThrow(SheetEntryNotFoundException::new);
        if (!ownDraft(a, requesterId)) {
            throw new InvalidMartialArtException("Só o autor envia um rascunho para aprovação");
        }
        List<String> issues = new ArrayList<>();
        for (ArtTechnique t : a.getTechniques()) {
            AssemblyRules.checkTechnique(a.getCategory(), t).issues().forEach(i -> issues.add(t.getName() + ": " + i));
        }
        for (ArtBase b : a.getBases()) {
            AssemblyRules.checkBase(a.getCategory(), b).issues().forEach(i -> issues.add(b.getName() + ": " + i));
        }
        if (!issues.isEmpty()) {
            throw new InvalidMartialArtException("Corrija a montagem antes de enviar: " + String.join("; ", issues));
        }
        a.setStatus(ArtStatus.PENDING_APPROVAL);
        a.setReviewNote(null);
        return toResponse(artRepository.save(a));
    }

    public MartialArtResponse reviewArt(UUID id, ReviewArtRequest req, UUID requesterId) {
        MartialArt a = artRepository.findById(id).orElseThrow(SheetEntryNotFoundException::new);
        requireWrite(a.getCampaignId(), requesterId);
        if (a.getStatus() != ArtStatus.PENDING_APPROVAL) {
            throw new InvalidMartialArtException("A arte não está aguardando aprovação");
        }
        a.setStatus(Boolean.TRUE.equals(req.approved()) ? ArtStatus.APPROVED : ArtStatus.DRAFT);
        a.setReviewNote(req.reason());
        return toResponse(artRepository.save(a));
    }

    public List<AssemblyModule> modules() {
        if (modules == null) {
            try (var in = new ClassPathResource("catalog/modules.json").getInputStream()) {
                modules = List.copyOf(new ObjectMapper().readValue(in, new TypeReference<List<AssemblyModule>>() { }));
            } catch (java.io.IOException e) {
                throw new IllegalStateException("catalog/modules.json ilegível", e);
            }
        }
        return modules;
    }

    public AssemblyCheck checkAssembly(AssemblyCheckRequest req) {
        if ((req.technique() == null) == (req.base() == null)) {
            throw new InvalidMartialArtException("Informe uma técnica ou uma base");
        }
        return req.technique() != null
                ? AssemblyRules.checkTechnique(req.category(), req.technique())
                : AssemblyRules.checkBase(req.category(), req.base());
    }

    private static boolean approved(MartialArt a) {
        return a.getStatus() == null || a.getStatus() == ArtStatus.APPROVED;
    }

    private static boolean ownDraft(MartialArt a, UUID requesterId) {
        return a.getStatus() == ArtStatus.DRAFT && requesterId.equals(a.getAuthorId());
    }

    private boolean visible(MartialArt a, UUID requesterId) {
        if (approved(a) || requesterId.equals(a.getAuthorId())) return true;
        return a.getStatus() == ArtStatus.PENDING_APPROVAL && a.getCampaignId() != null
                && isMaster(a.getCampaignId(), requesterId);
    }

    private boolean isMaster(UUID campaignId, UUID userId) {
        return memberRepository.existsByCampaignIdAndUserIdAndRole(campaignId, userId, Role.MASTER);
    }

    // ---------- Instintos e Marcos (F4b) ----------

    public List<InstinctResponse> listInstincts(UUID campaignIdOrNull, UUID requesterId) {
        if (campaignIdOrNull != null) requireMember(campaignIdOrNull, requesterId);
        return instinctsFor(campaignIdOrNull).stream()
                .sorted(Comparator.comparing(Instinct::getRank).thenComparing(Instinct::getName))
                .map(i -> new InstinctResponse(i.getId(), i.getCampaignId(), i.getName(), i.getRank(), i.getDescription()))
                .toList();
    }

    public List<FightMilestoneResponse> listMilestones(UUID campaignIdOrNull, UUID requesterId) {
        if (campaignIdOrNull != null) requireMember(campaignIdOrNull, requesterId);
        return milestonesFor(campaignIdOrNull).stream()
                .sorted(Comparator.comparing(FightMilestone::getCategory).thenComparing(FightMilestone::getName))
                .map(m -> new FightMilestoneResponse(m.getId(), m.getCampaignId(), m.getName(), m.getCategory(),
                        m.getDescription(), m.getAutoTrigger(), m.isGrantsArtPoint(), m.isSeriousOnly(),
                        m.getMinOtherDisadvantage()))
                .toList();
    }

    /** Globais + da campanha; item da campanha com o mesmo nome substitui o global. */
    public List<Instinct> instinctsFor(UUID campaignIdOrNull) {
        List<Instinct> global = instinctRepository.findByCampaignIdIsNull();
        if (campaignIdOrNull == null) return global;
        List<Instinct> own = instinctRepository.findByCampaignId(campaignIdOrNull);
        List<Instinct> result = new ArrayList<>(own);
        global.stream().filter(g -> own.stream().noneMatch(o -> o.getName().equals(g.getName()))).forEach(result::add);
        return result;
    }

    public List<FightMilestone> milestonesFor(UUID campaignIdOrNull) {
        List<FightMilestone> global = milestoneRepository.findByCampaignIdIsNull();
        if (campaignIdOrNull == null) return global;
        List<FightMilestone> own = milestoneRepository.findByCampaignId(campaignIdOrNull);
        List<FightMilestone> result = new ArrayList<>(own);
        global.stream().filter(g -> own.stream().noneMatch(o -> o.getName().equals(g.getName()))).forEach(result::add);
        return result;
    }

    // ---------- import ----------

    public CatalogImportResponse importCatalog(UUID campaignIdOrNull, CatalogImportRequest req, UUID requesterId) {
        requireWrite(campaignIdOrNull, requesterId);
        int traits = 0;
        int clans = 0;
        int arts = 0;
        if (req.traits() != null) {
            List<TraitDefinition> existing = scopeTraits(campaignIdOrNull);
            for (TraitPayload p : req.traits()) {
                TraitDefinition t = existing.stream()
                        .filter(e -> e.getName().equals(p.name()) && e.getRarity() == p.rarity())
                        .findFirst().orElse(null);
                if (t == null) {
                    t = new TraitDefinition();
                    t.setId(UUID.randomUUID());
                    t.setCampaignId(campaignIdOrNull);
                    t.setVersion(0);
                    existing.add(t);
                }
                apply(t, p);
                traitRepository.save(t);
                traits++;
            }
        }
        if (req.clans() != null) {
            List<Clan> existing = scopeClans(campaignIdOrNull);
            for (ClanPayload p : req.clans()) {
                Clan c = existing.stream().filter(e -> e.getName().equals(p.name())).findFirst().orElse(null);
                if (c == null) {
                    c = new Clan();
                    c.setId(UUID.randomUUID());
                    c.setCampaignId(campaignIdOrNull);
                    c.setVersion(0);
                    existing.add(c);
                }
                apply(c, p);
                clanRepository.save(c);
                clans++;
            }
        }
        if (req.arts() != null) {
            List<MartialArt> existing = new ArrayList<>(campaignIdOrNull == null
                    ? artRepository.findByCampaignIdIsNull() : artRepository.findByCampaignId(campaignIdOrNull));
            for (MartialArtPayload p : req.arts()) {
                MartialArt a = existing.stream().filter(e -> e.getName().equals(p.name())).findFirst().orElse(null);
                if (a == null) {
                    a = new MartialArt();
                    a.setId(UUID.randomUUID());
                    a.setCampaignId(campaignIdOrNull);
                    a.setVersion(0);
                    existing.add(a);
                }
                apply(a, p);
                artRepository.save(a);
                arts++;
            }
        }
        int instincts = 0;
        if (req.instincts() != null) {
            List<Instinct> existing = new ArrayList<>(campaignIdOrNull == null
                    ? instinctRepository.findByCampaignIdIsNull() : instinctRepository.findByCampaignId(campaignIdOrNull));
            for (InstinctPayload p : req.instincts()) {
                Instinct i = existing.stream().filter(e -> e.getName().equals(p.name())).findFirst().orElse(null);
                if (i == null) {
                    i = Instinct.builder().id(UUID.randomUUID()).campaignId(campaignIdOrNull).build();
                    existing.add(i);
                }
                i.setName(p.name());
                i.setRank(p.rank());
                i.setDescription(p.description());
                i.setVersion(i.getVersion() + 1);
                i.setUpdatedAt(Instant.now());
                instinctRepository.save(i);
                instincts++;
            }
        }
        int milestones = 0;
        if (req.milestones() != null) {
            List<FightMilestone> existing = new ArrayList<>(campaignIdOrNull == null
                    ? milestoneRepository.findByCampaignIdIsNull() : milestoneRepository.findByCampaignId(campaignIdOrNull));
            for (FightMilestonePayload p : req.milestones()) {
                FightMilestone m = existing.stream().filter(e -> e.getName().equals(p.name())).findFirst().orElse(null);
                if (m == null) {
                    m = FightMilestone.builder().id(UUID.randomUUID()).campaignId(campaignIdOrNull).build();
                    existing.add(m);
                }
                m.setName(p.name());
                m.setCategory(p.category());
                m.setDescription(p.description());
                apply(m, p);
                m.setVersion(m.getVersion() + 1);
                m.setUpdatedAt(Instant.now());
                milestoneRepository.save(m);
                milestones++;
            }
        }
        return new CatalogImportResponse(traits, clans, arts, instincts, milestones);
    }

    // ---------- visão da campanha ----------

    public List<TraitDefinition> traitsForCampaign(UUID campaignId) {
        List<TraitDefinition> own = traitRepository.findByCampaignId(campaignId);
        List<TraitDefinition> result = new ArrayList<>(own);
        for (TraitDefinition g : traitRepository.findByCampaignIdIsNull()) {
            boolean replaced = g.getRollNumber() != null && own.stream().anyMatch(c ->
                    c.getRollNumber() != null && c.getRarity() == g.getRarity()
                            && c.getRollNumber().equals(g.getRollNumber()));
            if (!replaced) result.add(g);
        }
        return result;
    }

    public List<Clan> clansForCampaign(UUID campaignId) {
        List<Clan> own = clanRepository.findByCampaignId(campaignId);
        List<Clan> result = new ArrayList<>(own);
        for (Clan g : clanRepository.findByCampaignIdIsNull()) {
            boolean replaced = g.getRollNumber() != null
                    && own.stream().anyMatch(c -> g.getRollNumber().equals(c.getRollNumber()));
            if (!replaced) result.add(g);
        }
        return result;
    }

    public Optional<TraitDefinition> findTrait(UUID id) {
        return traitRepository.findById(id);
    }

    public Optional<Clan> findClan(UUID id) {
        return clanRepository.findById(id);
    }

    // ---------- helpers ----------

    private List<TraitDefinition> scopeTraits(UUID campaignIdOrNull) {
        return new ArrayList<>(campaignIdOrNull == null
                ? traitRepository.findByCampaignIdIsNull() : traitRepository.findByCampaignId(campaignIdOrNull));
    }

    private List<Clan> scopeClans(UUID campaignIdOrNull) {
        return new ArrayList<>(campaignIdOrNull == null
                ? clanRepository.findByCampaignIdIsNull() : clanRepository.findByCampaignId(campaignIdOrNull));
    }

    private boolean isGlobalEditor(UUID userId) {
        return !editors.isEmpty() && userRepository.findById(userId)
                .map(u -> u.getEmail() != null && editors.contains(u.getEmail().trim().toLowerCase()))
                .orElse(false);
    }

    private void requireWrite(UUID campaignIdOrNull, UUID requesterId) {
        if (campaignIdOrNull == null) {
            if (!isGlobalEditor(requesterId)) {
                throw new AccessDeniedException("Apenas editores do catálogo global podem realizar esta ação");
            }
        } else if (!memberRepository.existsByCampaignIdAndUserIdAndRole(campaignIdOrNull, requesterId, Role.MASTER)) {
            throw new AccessDeniedException("Apenas o Mestre da campanha pode realizar esta ação");
        }
    }

    private void requireMember(UUID campaignId, UUID requesterId) {
        if (!memberRepository.existsByCampaignIdAndUserId(campaignId, requesterId)) {
            throw new AccessDeniedException("Você não é membro desta campanha");
        }
    }

    private static void apply(TraitDefinition t, TraitPayload p) {
        t.setName(p.name());
        t.setRarity(p.rarity());
        t.setCategory(p.category());
        t.setRollNumber(p.rollNumber());
        t.setDescription(p.description());
        t.setEffects(p.effects());
        t.setBuildGains(p.buildGains());
        t.setDevelopable(Boolean.TRUE.equals(p.developable()));
        t.setUpgradable(Boolean.TRUE.equals(p.upgradable()));
        t.setActivation(p.activation());
        t.setRequirements(p.requirements());
        t.setDevelopmentRequirements(p.developmentRequirements());
        t.setVariations(p.variations() == null ? new ArrayList<>() : p.variations());
        t.setVersion(t.getVersion() + 1);
        t.setUpdatedAt(Instant.now());
    }

    /** Campos de um Marco vindos da importação (também usado pelo seed). */
    public static void apply(FightMilestone m, FightMilestonePayload p) {
        m.setAutoTrigger(p.autoTrigger());
        m.setGrantsArtPoint(Boolean.TRUE.equals(p.grantsArtPoint()));
        m.setSeriousOnly(Boolean.TRUE.equals(p.seriousOnly()));
        m.setMinOtherDisadvantage(p.minOtherDisadvantage());
    }

    private static void apply(Clan c, ClanPayload p) {
        c.setName(p.name());
        c.setRollNumber(p.rollNumber());
        c.setDescription(p.description());
        c.setBuildGains(p.buildGains());
        c.setHeritage(p.heritage());
        c.setVersion(c.getVersion() + 1);
        c.setUpdatedAt(Instant.now());
    }

    private static TraitDefinitionResponse toResponse(TraitDefinition t) {
        return new TraitDefinitionResponse(t.getId(), t.getCampaignId(), t.getName(), t.getRarity(), t.getCategory(),
                t.getRollNumber(), t.getDescription(), t.getEffects(), t.getBuildGains(),
                t.isDevelopable(), t.isUpgradable(), t.getVersion(),
                t.getActivation(), t.getRequirements(), t.getDevelopmentRequirements(), t.getVariations());
    }

    private static ClanResponse toResponse(Clan c) {
        return new ClanResponse(c.getId(), c.getCampaignId(), c.getName(), c.getRollNumber(), c.getDescription(),
                c.getBuildGains(), c.getHeritage(), c.getVersion());
    }

    private static void apply(MartialArt a, MartialArtPayload p) {
        a.setName(p.name());
        a.setCategory(p.category());
        a.setRank(p.rank());
        a.setMartialType(p.martialType());
        a.setAttackSpecializations(p.attackSpecializations());
        a.setReactionSpecializations(p.reactionSpecializations());
        a.setRequirement(p.requirement());
        a.setDescription(p.description());
        a.setSections(p.sections());
        a.setPassives(p.passives());
        a.setBases(p.bases() == null ? new ArrayList<>() : p.bases());
        a.setTechniques(p.techniques() == null ? new ArrayList<>() : p.techniques());
        a.setBorrowed(p.borrowed() == null ? new ArrayList<>() : p.borrowed());
        a.setVersion(a.getVersion() + 1);
        a.setUpdatedAt(Instant.now());
    }

    private static MartialArtSummaryResponse toSummary(MartialArt a) {
        return new MartialArtSummaryResponse(a.getId(), a.getCampaignId(), a.getName(), a.getCategory(),
                a.getMartialType(), a.getAttackSpecializations(), a.getReactionSpecializations(),
                a.getBases() == null ? 0 : a.getBases().size(),
                a.getTechniques() == null ? 0 : a.getTechniques().size(), a.getVersion(), a.getStatus(), a.getAuthorId());
    }

    private static MartialArtResponse toResponse(MartialArt a) {
        return new MartialArtResponse(a.getId(), a.getCampaignId(), a.getName(), a.getCategory(), a.getRank(),
                a.getMartialType(), a.getAttackSpecializations(), a.getReactionSpecializations(), a.getRequirement(),
                a.getDescription(), a.getSections(), a.getPassives(), a.getBases(), a.getTechniques(),
                a.getVersion(), a.getUpdatedAt(), a.getStatus(), a.getAuthorId(), a.getReviewNote(),
                a.getTechniques() == null ? List.of()
                        : a.getTechniques().stream().map(t -> AssemblyRules.checkTechnique(a.getCategory(), t)).toList(),
                a.getBases() == null ? List.of()
                        : a.getBases().stream().map(b -> AssemblyRules.checkBase(a.getCategory(), b)).toList(),
                a.getBorrowed() == null ? List.of() : a.getBorrowed());
    }
}
