package com.tatakai.manager.npc.service;

import com.tatakai.manager.build.entity.BuildStatus;
import com.tatakai.manager.npc.repository.CampaignNpcRepository;
import com.tatakai.manager.catalog.repository.MartialArtRepository;
import com.tatakai.manager.martial.entity.CharacterArt;
import com.tatakai.manager.martial.entity.CharacterArtStatus;
import com.tatakai.manager.martial.repository.CharacterArtRepository;
import com.tatakai.manager.npc.entity.Npc;
import com.tatakai.manager.npc.entity.NpcAttributes;
import com.tatakai.manager.npc.entity.NpcDetail;
import com.tatakai.manager.npc.repository.NpcRepository;
import com.tatakai.manager.sheet.entity.CharacterSheet;
import com.tatakai.manager.sheet.entity.CharacterTrait;
import com.tatakai.manager.sheet.entity.NpcSheetVisibility;
import com.tatakai.manager.sheet.entity.SheetSection;
import com.tatakai.manager.sheet.repository.CharacterSheetRepository;
import com.tatakai.manager.sheet.repository.NpcSheetVisibilityRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;

/**
 * Migra NPCs antigos para a ficha única (§51.8): garante uma ficha por NPC e move os traços em texto, a arte marcial e
 * (se a ficha ainda não existia) os 6 atributos legados do Mongo para ela. Idempotente: roda a cada boot, pula o que
 * já existe e limpa os campos legados do Mongo só depois do commit do Postgres.
 */
@Component
public class NpcSheetMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NpcSheetMigrator.class);

    private final NpcRepository npcs;
    private final CharacterSheetRepository sheets;
    private final CharacterArtRepository characterArts;
    private final MartialArtRepository martialArts;
    private final CampaignNpcRepository campaignNpcs;
    private final NpcSheetVisibilityRepository visibility;
    private final TransactionTemplate tx;

    public NpcSheetMigrator(NpcRepository npcs, CharacterSheetRepository sheets, CharacterArtRepository characterArts,
                            MartialArtRepository martialArts, CampaignNpcRepository campaignNpcs,
                            NpcSheetVisibilityRepository visibility, TransactionTemplate tx) {
        this.npcs = npcs;
        this.sheets = sheets;
        this.characterArts = characterArts;
        this.martialArts = martialArts;
        this.campaignNpcs = campaignNpcs;
        this.visibility = visibility;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        int done = 0;
        for (Npc npc : npcs.findAll()) {
            try {
                tx.executeWithoutResult(s -> migrate(npc));
                if (clearLegacy(npc)) npcs.save(npc);
                done++;
            } catch (RuntimeException e) {
                log.warn("Migração do NPC {} falhou (será tentada no próximo boot): {}", npc.getId(), e.getMessage());
            }
        }
        if (done > 0) log.info("Fichas de NPC verificadas/migradas: {}", done);
    }

    /** Parte JPA (dentro de transação). Não mexe no Mongo. */
    public void migrate(Npc npc) {
        CharacterSheet sheet = sheets.findByNpcId(npc.getId()).orElse(null);
        if (sheet == null) {
            sheet = CharacterSheet.builder().npcId(npc.getId()).npcOwnerId(npc.getOwnerId()).name(npc.getName())
                    .buildStatus(BuildStatus.COMPLETED).combatReady(false).build();
            copyAttributes(sheet, npc.getAttributes()); // só pré-preenchimento: continua leve
        }
        if (sheet.getTraits() == null) sheet.setTraits(new ArrayList<>());

        boolean traitsAdded = false;
        if (npc.getTraits() != null) {
            for (NpcDetail d : npc.getTraits()) {
                if (d.getName() == null || sheet.getTraits().stream().anyMatch(t -> d.getName().equals(t.getName()))) continue;
                sheet.getTraits().add(CharacterTrait.builder().sheet(sheet).name(d.getName())
                        .description(d.getDescription()).build());
                traitsAdded = true;
            }
        }
        sheet = sheets.save(sheet);

        if (npc.getMartialArtId() != null && characterArts.findBySheetIdOrderByCreatedAt(sheet.getId()).stream()
                .noneMatch(a -> npc.getMartialArtId().equals(a.getArtId()))) {
            var art = martialArts.findById(npc.getMartialArtId()).orElse(null);
            characterArts.save(CharacterArt.builder().sheet(sheet).artId(npc.getMartialArtId())
                    .artName(art == null ? null : art.getName()).category(art == null ? null : art.getCategory())
                    .graduation(npc.getMartialGraduation()).status(CharacterArtStatus.ACTIVE).build());
        }

        // Os jogadores já viam os traços em texto: mantém visível onde o NPC é visível
        if (traitsAdded) {
            for (var cn : campaignNpcs.findByNpcId(npc.getId())) {
                if (cn.isVisible() && visibility.findByCampaignNpcIdAndSection(cn.getId(), SheetSection.TRAITS).isEmpty()) {
                    visibility.save(NpcSheetVisibility.builder().campaignNpc(cn).section(SheetSection.TRAITS).visible(true).build());
                }
            }
        }
    }

    /** Limpa os campos legados já migrados; devolve se algo mudou. */
    public static boolean clearLegacy(Npc npc) {
        boolean dirty = npc.getAttributes() != null || npc.getMartialArtId() != null
                || (npc.getTraits() != null && !npc.getTraits().isEmpty());
        npc.setAttributes(null);
        npc.setMartialArtId(null);
        npc.setMartialGraduation(null);
        if (npc.getTraits() != null) npc.getTraits().clear();
        return dirty;
    }

    private static void copyAttributes(CharacterSheet s, NpcAttributes a) {
        if (a == null) return;
        s.setStr(n(a.getForca()));
        s.setDex(n(a.getDestreza()));
        s.setCon(n(a.getConstituicao()));
        s.setIntl(n(a.getInteligencia()));
        s.setMen(n(a.getMental()));
        s.setTal(n(a.getTalento()));
    }

    private static int n(Short v) {
        return v == null ? 0 : v;
    }
}
