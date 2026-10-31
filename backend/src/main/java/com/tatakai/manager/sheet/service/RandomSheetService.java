package com.tatakai.manager.sheet.service;

import com.tatakai.manager.catalog.dto.response.MartialArtSummaryResponse;
import com.tatakai.manager.catalog.entity.Graduation;
import com.tatakai.manager.catalog.service.CatalogService;
import com.tatakai.manager.martial.dto.request.CharacterArtRequest;
import com.tatakai.manager.martial.dto.request.GraduationRequest;
import com.tatakai.manager.martial.service.CharacterTechniqueService;
import com.tatakai.manager.sheet.dto.request.AttributesPayload;
import com.tatakai.manager.sheet.dto.request.CreateCharacterSheetRequest;
import com.tatakai.manager.sheet.dto.request.CreateGmSheetRequest;
import com.tatakai.manager.sheet.dto.request.GmAdjustmentRequest;
import com.tatakai.manager.sheet.dto.request.RandomSheetRequest;
import com.tatakai.manager.sheet.dto.response.CharacterSheetResponse;
import com.tatakai.manager.sheet.entity.Attribute;
import com.tatakai.manager.sheet.entity.GmAction;
import com.tatakai.manager.sheet.service.rules.RandomSheetRules;
import com.tatakai.manager.sheet.service.rules.RandomSheetRules.TraitRef;
import com.tatakai.manager.shared.exception.AccessDeniedException;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Ficha aleatória do Mestre (§51.9): sorteia pelas regras puras ({@link RandomSheetRules}) e monta a ficha reusando os
 * serviços existentes (ficha instantânea, ajuste do Mestre, artes). Não é uma transação única, como a criação de NPC.
 */
@Service
public class RandomSheetService {

    private static final String REASON = "Ficha aleatória";

    private final CharacterSheetService sheets;
    private final CharacterTechniqueService techniques;
    private final CatalogService catalog;

    public RandomSheetService(CharacterSheetService sheets, CharacterTechniqueService techniques, CatalogService catalog) {
        this.sheets = sheets;
        this.techniques = techniques;
        this.catalog = catalog;
    }

    public CharacterSheetResponse create(UUID campaignId, RandomSheetRequest req, UUID requesterId) {
        Random rnd = new Random();

        // arte escolhida (a lista da campanha só abre para membros; criar a ficha exige Mestre)
        MartialArtSummaryResponse art = null;
        if (req.artMode() != RandomSheetRequest.ArtMode.NONE) {
            List<MartialArtSummaryResponse> arts = catalog.listArts(campaignId, requesterId).stream()
                    .filter(a -> a.status() == null || a.status() == com.tatakai.manager.catalog.entity.ArtStatus.APPROVED)
                    .toList();
            if (req.artMode() == RandomSheetRequest.ArtMode.SPECIFIC) {
                art = arts.stream().filter(a -> a.id().equals(req.artId())).findFirst()
                        .orElseThrow(com.tatakai.manager.sheet.exception.SheetEntryNotFoundException::new);
            } else if (!arts.isEmpty()) {
                art = arts.get(rnd.nextInt(arts.size()));
            }
        }

        var focus = art == null ? null : RandomSheetRules.focusFor(art.martialType(), rnd);
        var gen = RandomSheetRules.generate(req.profile(), focus, rnd);
        var a = gen.attributes();
        var sheetReq = new CreateCharacterSheetRequest(
                req.name() == null || req.name().isBlank() ? "NPC aleatório" : req.name().trim(),
                gen.bodyType(), gen.dominance(), gen.heightM(), null,
                new AttributesPayload(a.get(Attribute.STR), a.get(Attribute.DEX), a.get(Attribute.CON),
                        a.get(Attribute.INT), a.get(Attribute.CHA), a.get(Attribute.MEN), a.get(Attribute.TAL)));
        CharacterSheetResponse sheet = sheets.createGmSheet(campaignId, new CreateGmSheetRequest(sheetReq, req.visible()),
                requesterId);

        if (gen.el() > 0) {
            sheets.applyGmAdjustment(sheet.id(), new GmAdjustmentRequest(GmAction.GRANT_FIGHT_EXPERIENCE, null, gen.el(),
                    REASON, null, null, null), requesterId);
        }

        List<RandomSheetRules.TraitLine> lines = req.traits() == null || req.traits().isEmpty()
                ? List.of(new RandomSheetRules.TraitLine(req.profile().traits, null, null, null))
                : req.traits().stream().map(r -> new RandomSheetRules.TraitLine(r.count(), r.rarity(), r.category(),
                        r.developable())).toList();
        List<TraitRef> pool = catalog.traitsForCampaign(campaignId).stream()
                .map(t -> new TraitRef(t.getId(), t.getRarity(), t.getCategory(), t.isDevelopable())).toList();
        for (TraitRef t : RandomSheetRules.pickTraits(pool, lines, rnd)) {
            sheets.applyGmAdjustment(sheet.id(), new GmAdjustmentRequest(GmAction.ADD_TRAIT, null, null, REASON,
                    null, null, t.id()), requesterId);
        }

        if (art != null) {
            final UUID artId = art.id();
            var martial = techniques.requestArt(sheet.id(), new CharacterArtRequest(artId), requesterId);
            Graduation grad = req.graduation() != null ? req.graduation() : req.profile().grad;
            var ca = martial.arts().stream().filter(x -> x.artId().equals(artId)).findFirst();
            if (ca.isPresent() && grad != Graduation.BEGINNER) {
                techniques.setGraduation(sheet.id(), ca.get().id(), new GraduationRequest(grad), requesterId);
            }
        }
        return sheets.get(sheet.id(), requesterId);
    }
}
