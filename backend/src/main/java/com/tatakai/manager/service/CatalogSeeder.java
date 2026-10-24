package com.tatakai.manager.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tatakai.manager.dto.request.ClanPayload;
import com.tatakai.manager.dto.request.FightMilestonePayload;
import com.tatakai.manager.dto.request.InstinctPayload;
import com.tatakai.manager.entity.FightMilestone;
import com.tatakai.manager.entity.Instinct;
import com.tatakai.manager.repository.FightMilestoneRepository;
import com.tatakai.manager.repository.InstinctRepository;
import com.tatakai.manager.dto.request.MartialArtPayload;
import com.tatakai.manager.dto.request.TraitPayload;
import com.tatakai.manager.entity.Clan;
import com.tatakai.manager.entity.MartialArt;
import com.tatakai.manager.entity.TraitDefinition;
import com.tatakai.manager.repository.ClanRepository;
import com.tatakai.manager.repository.MartialArtRepository;
import com.tatakai.manager.repository.TraitDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Carrega o catálogo global inicial (classpath catalog/*.json) só se a coleção não tiver itens globais. */
@Component
public class CatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);

    private final TraitDefinitionRepository traitRepository;
    private final ClanRepository clanRepository;
    private final ObjectMapper objectMapper;
    private final MartialArtRepository artRepository;
    private final InstinctRepository instinctRepository;
    private final FightMilestoneRepository milestoneRepository;

    public CatalogSeeder(TraitDefinitionRepository traitRepository, ClanRepository clanRepository,
                         ObjectMapper objectMapper, MartialArtRepository artRepository,
                         InstinctRepository instinctRepository, FightMilestoneRepository milestoneRepository) {
        this.artRepository = artRepository;
        this.instinctRepository = instinctRepository;
        this.milestoneRepository = milestoneRepository;
        this.traitRepository = traitRepository;
        this.clanRepository = clanRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (traitRepository.findByCampaignIdIsNull().isEmpty()) {
            List<TraitPayload> traits = read("catalog/traits.json", new TypeReference<>() {});
            for (TraitPayload p : traits) {
                TraitDefinition t = new TraitDefinition();
                t.setId(UUID.randomUUID());
                t.setName(p.name());
                t.setRarity(p.rarity());
                t.setCategory(p.category());
                t.setRollNumber(p.rollNumber());
                t.setDescription(p.description());
                t.setEffects(p.effects());
                t.setVariations(p.variations() == null ? new java.util.ArrayList<>() : p.variations());
                t.setBuildGains(p.buildGains());
                t.setDevelopable(Boolean.TRUE.equals(p.developable()));
                t.setUpgradable(Boolean.TRUE.equals(p.upgradable()));
                t.setActivation(p.activation());
                t.setRequirements(p.requirements());
                t.setDevelopmentRequirements(p.developmentRequirements());
                t.setVersion(1);
                t.setUpdatedAt(Instant.now());
                traitRepository.save(t);
            }
            log.info("Catálogo: {} traços globais carregados", traits.size());
        }
        if (clanRepository.findByCampaignIdIsNull().isEmpty()) {
            List<ClanPayload> clans = read("catalog/clans.json", new TypeReference<>() {});
            for (ClanPayload p : clans) {
                Clan c = new Clan();
                c.setId(UUID.randomUUID());
                c.setName(p.name());
                c.setRollNumber(p.rollNumber());
                c.setDescription(p.description());
                c.setBuildGains(p.buildGains());
                c.setHeritage(p.heritage());
                c.setVersion(1);
                c.setUpdatedAt(Instant.now());
                clanRepository.save(c);
            }
            log.info("Catálogo: {} clãs globais carregados", clans.size());
        }
        if (artRepository.findByCampaignIdIsNull().isEmpty()) {
            List<MartialArtPayload> arts = read("catalog/arts.json", new TypeReference<>() {});
            for (MartialArtPayload p : arts) {
                MartialArt a = new MartialArt();
                a.setId(UUID.randomUUID());
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
                a.setBorrowed(p.borrowed() == null ? new java.util.ArrayList<>() : p.borrowed());
                a.setVersion(1);
                a.setUpdatedAt(Instant.now());
                artRepository.save(a);
            }
            log.info("Catálogo: {} artes marciais globais carregadas", arts.size());
        }
        if (instinctRepository.findByCampaignIdIsNull().isEmpty()) {
            List<InstinctPayload> instincts = read("catalog/instincts.json", new TypeReference<>() {});
            for (InstinctPayload p : instincts) {
                instinctRepository.save(Instinct.builder().id(UUID.randomUUID()).name(p.name()).rank(p.rank())
                        .description(p.description()).version(1).updatedAt(Instant.now()).build());
            }
            log.info("Catálogo: {} Instintos globais carregados", instincts.size());
        }
        if (milestoneRepository.findByCampaignIdIsNull().isEmpty()) {
            List<FightMilestonePayload> milestones = read("catalog/milestones.json", new TypeReference<>() {});
            for (FightMilestonePayload p : milestones) {
                FightMilestone m = FightMilestone.builder().id(UUID.randomUUID()).name(p.name()).category(p.category())
                        .description(p.description()).version(1).updatedAt(Instant.now()).build();
                CatalogService.apply(m, p);
                milestoneRepository.save(m);
            }
            log.info("Catálogo: {} Marcos globais carregados", milestones.size());
        }
    }

    private <T> List<T> read(String path, TypeReference<List<T>> type) {
        ClassPathResource res = new ClassPathResource(path);
        if (!res.exists()) return List.of();
        try (InputStream in = res.getInputStream()) {
            List<T> list = objectMapper.readValue(in, type);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            log.warn("Catálogo: não foi possível ler {}: {}", path, e.getMessage());
            return List.of();
        }
    }
}
