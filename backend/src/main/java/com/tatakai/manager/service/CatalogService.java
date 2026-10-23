package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.CatalogImportRequest;
import com.tatakai.manager.dto.request.ClanPayload;
import com.tatakai.manager.dto.request.MartialArtPayload;
import com.tatakai.manager.dto.request.TraitPayload;
import com.tatakai.manager.dto.response.CatalogImportResponse;
import com.tatakai.manager.dto.response.CatalogPermissionsResponse;
import com.tatakai.manager.dto.response.ClanResponse;
import com.tatakai.manager.dto.response.MartialArtResponse;
import com.tatakai.manager.dto.response.MartialArtSummaryResponse;
import com.tatakai.manager.dto.response.TraitDefinitionResponse;
import com.tatakai.manager.entity.Clan;
import com.tatakai.manager.entity.MartialArt;
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

    public CatalogService(TraitDefinitionRepository traitRepository,
                          ClanRepository clanRepository,
                          CampaignMemberRepository memberRepository,
                          UserRepository userRepository,
                          @Value("${app.catalog.editors:}") String editors,
                          MartialArtRepository artRepository) {
        this.artRepository = artRepository;
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
            List<MartialArt> own = artRepository.findByCampaignId(campaignIdOrNull);
            Set<String> ownNames = own.stream().map(MartialArt::getName).collect(Collectors.toSet());
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
        return toResponse(a);
    }

    public void deleteArt(UUID id, UUID requesterId) {
        MartialArt a = artRepository.findById(id).orElseThrow(SheetEntryNotFoundException::new);
        requireWrite(a.getCampaignId(), requesterId);
        artRepository.delete(a);
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
        return new CatalogImportResponse(traits, clans, arts);
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
        a.setBases(p.bases());
        a.setTechniques(p.techniques());
        a.setVersion(a.getVersion() + 1);
        a.setUpdatedAt(Instant.now());
    }

    private static MartialArtSummaryResponse toSummary(MartialArt a) {
        return new MartialArtSummaryResponse(a.getId(), a.getCampaignId(), a.getName(), a.getCategory(),
                a.getMartialType(), a.getAttackSpecializations(), a.getReactionSpecializations(),
                a.getBases() == null ? 0 : a.getBases().size(),
                a.getTechniques() == null ? 0 : a.getTechniques().size(), a.getVersion());
    }

    private static MartialArtResponse toResponse(MartialArt a) {
        return new MartialArtResponse(a.getId(), a.getCampaignId(), a.getName(), a.getCategory(), a.getRank(),
                a.getMartialType(), a.getAttackSpecializations(), a.getReactionSpecializations(), a.getRequirement(),
                a.getDescription(), a.getSections(), a.getPassives(), a.getBases(), a.getTechniques(),
                a.getVersion(), a.getUpdatedAt());
    }
}
