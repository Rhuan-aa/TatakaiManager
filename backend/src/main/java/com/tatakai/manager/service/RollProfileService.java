package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.RollTestRequest;
import com.tatakai.manager.dto.response.RollProfileResponse;
import com.tatakai.manager.entity.ArtBase;
import com.tatakai.manager.entity.ArtTechnique;
import com.tatakai.manager.entity.AssemblyItem;
import com.tatakai.manager.entity.AssemblyItemKind;
import com.tatakai.manager.entity.AssemblyVariation;
import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.BasicAttack;
import com.tatakai.manager.entity.CharacterSheet;
import com.tatakai.manager.entity.CharacterTechnique;
import com.tatakai.manager.entity.FatigueBand;
import com.tatakai.manager.entity.MartialArt;
import com.tatakai.manager.entity.Mastery;
import com.tatakai.manager.entity.MasteryBonus;
import com.tatakai.manager.entity.MartialType;
import com.tatakai.manager.entity.MoveKind;
import com.tatakai.manager.entity.RollMode;
import com.tatakai.manager.entity.RollPart;
import com.tatakai.manager.entity.RollSourceType;
import com.tatakai.manager.entity.TechniqueCore;
import com.tatakai.manager.entity.TechniqueState;
import com.tatakai.manager.exception.InvalidRollException;
import com.tatakai.manager.exception.SheetEntryNotFoundException;
import com.tatakai.manager.repository.CharacterTechniqueRepository;
import com.tatakai.manager.repository.MartialArtRepository;
import com.tatakai.manager.service.rules.MartialRules;
import com.tatakai.manager.service.rules.BaseRules;
import com.tatakai.manager.service.rules.RollRules;
import com.tatakai.manager.service.rules.SheetRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

/**
 * Calcula o RollProfile (PLANO §20): parcelas de acerto/dano de uma técnica, ataque básico ou teste de
 * atributo, a partir da ficha + catálogo. Sem persistência; sem checagem de acesso (o chamador valida).
 */
@Service
public class RollProfileService {

    private static final String DICE_MODULE = "Dado Maior";
    private static final String PRECISION_MODULE = "Precisão";

    /** Opções do modo detalhado que mudam o cálculo. */
    /** `attackType` (tipo marcial do golpe) escolhe os itens de base com escopo ("Precisão +1 em Derrubada"). */
    public record Opts(UUID baseId, boolean attackerDowned, String region, Integer regionPenalty, MartialType attackType) {
        public static final Opts NONE = new Opts(null, false, null, null, null);
    }

    /** Resultado do cálculo; dice já vem multiplicado por (1+E). */
    public record Calc(RollSourceType type, UUID sourceId, String name, String variation, BasicAttack basic,
                       int spCost, int critMargin, int advantage, List<RollPart> hit,
                       RollRules.Dice dice, List<RollPart> damage) {}

    private final CharacterTechniqueRepository techniqueRepository;
    private final MartialArtRepository martialArtRepository;

    public RollProfileService(CharacterTechniqueRepository techniqueRepository,
                              MartialArtRepository martialArtRepository) {
        this.techniqueRepository = techniqueRepository;
        this.martialArtRepository = martialArtRepository;
    }

    /** Técnicas ACTIVE (uma por variação) + os 6 ataques básicos. */
    @Transactional(readOnly = true)
    public List<Calc> list(CharacterSheet sheet) {
        List<Calc> out = new ArrayList<>();
        for (CharacterTechnique t : techniqueRepository.findBySheetIdOrderByCreatedAt(sheet.getId())) {
            if (t.getKind() != MoveKind.TECHNIQUE || t.getState() != TechniqueState.ACTIVE) continue;
            ArtTechnique cat = catalog(sheet, t);
            List<AssemblyVariation> vars = variations(cat);
            if (vars.isEmpty()) {
                out.add(technique(sheet, t, null, Opts.NONE));
            } else {
                for (AssemblyVariation v : vars) {
                    if (!attackCore(v.getCore())) continue; // Reação e Preparação não são ataques
                    out.add(technique(sheet, t, v.getName(), Opts.NONE));
                }
            }
        }
        for (BasicAttack b : BasicAttack.values()) out.add(basic(sheet, b, Opts.NONE));
        return out;
    }

    /** Cálculo para um RollTestRequest; situacionais e base só valem no modo detalhado. */
    @Transactional(readOnly = true)
    public Calc forRequest(CharacterSheet sheet, RollTestRequest req) {
        boolean detailed = req.mode() == RollMode.DETAILED;
        Opts o = detailed
                ? new Opts(req.baseId(), req.attackerDowned(), req.localizedRegion(), req.localizedPenalty(), null)
                : Opts.NONE;
        return switch (req.sourceType()) {
            case TECHNIQUE -> {
                if (req.sourceId() == null) throw new InvalidRollException("Informe a técnica");
                CharacterTechnique t = techniqueRepository.findById(req.sourceId())
                        .filter(x -> x.getSheet().getId().equals(sheet.getId()) && x.getState() == TechniqueState.ACTIVE)
                        .orElseThrow(SheetEntryNotFoundException::new);
                yield technique(sheet, t, req.variation(), o);
            }
            case BASIC_ATTACK -> {
                if (req.basicAttack() == null) throw new InvalidRollException("Informe o ataque básico");
                yield basic(sheet, req.basicAttack(), o);
            }
            case ATTRIBUTE_TEST -> {
                if (req.attribute() == null) throw new InvalidRollException("Informe o atributo");
                yield attribute(sheet, req.attribute());
            }
            case CUSTOM -> throw new InvalidRollException("Tipo de rolagem inválido para teste");
        };
    }

    @Transactional(readOnly = true)
    public Calc technique(CharacterSheet sheet, CharacterTechnique t, String variationName, Opts o) {
        if (t.getKind() != MoveKind.TECHNIQUE) throw new InvalidRollException("Apenas técnicas têm perfil de rolagem");
        ArtTechnique cat = catalog(sheet, t);
        List<AssemblyVariation> vars = variations(cat);
        AssemblyVariation variation = null;
        if (!vars.isEmpty()) {
            variation = variationName == null ? vars.get(0) : vars.stream()
                    .filter(v -> variationName.equals(v.getName())).findFirst()
                    .orElseThrow(() -> new InvalidRollException("Variação não encontrada"));
        }
        List<AssemblyItem> items = new ArrayList<>();
        if (variation != null && variation.getItems() != null) items.addAll(variation.getItems());
        if (cat != null && cat.getExtras() != null) items.addAll(cat.getExtras());

        List<MasteryBonus> bonuses = t.getBonuses() == null ? List.of() : t.getBonuses();
        int hitBonus = count(bonuses, MasteryBonus.HIT);
        int damageBonus = count(bonuses, MasteryBonus.DAMAGE);
        boolean apex = bonuses.contains(MasteryBonus.APEX) || t.getMastery() == Mastery.MASTERED;

        int degree = qty(items, DICE_MODULE);
        int precision = qty(items, PRECISION_MODULE);
        BaseRules.Profile base = base(sheet, o.baseId());

        List<RollPart> hit = new ArrayList<>(attackHit(sheet, o));
        if (precision > 0) hit.add(part("HIT", "Precisão (variação)", precision));
        if (hitBonus > 0) hit.add(part("HIT", "Bonificação HIT", hitBonus));
        hit.addAll(baseHit(base, o));
        hit.addAll(localized(o));
        hit.addAll(fatigueParts(sheet, physical(sheet)));

        List<RollPart> damage = new ArrayList<>();
        damage.add(part("DAMAGE", "FOR", RollRules.modOrZero(sheet.getStr())));
        if (damageBonus > 0) damage.add(part("DAMAGE", "Bonificação DAMAGE", 2 * damageBonus));
        damage.addAll(baseDamage(base, o));

        RollRules.Dice scale = RollRules.diceScale(degree);
        int e = RollRules.strengthExtra(sheet.getStr(), apex);
        RollRules.Dice dice = new RollRules.Dice(scale.count() * (1 + e), scale.sides());
        int sp = RollRules.spCost(cat == null ? null : cat.getSpCostBase(), count(bonuses, MasteryBonus.COST),
                physical(sheet));
        int adv = t.getMastery() == Mastery.SKETCH ? -1 : 0;
        return new Calc(RollSourceType.TECHNIQUE, t.getId(), t.getName(), variation == null ? null : variation.getName(),
                null, sp, RollRules.CRIT_MARGIN, adv, hit, dice, damage);
    }

    /** A variação (ou os extras) da técnica tem algum item cujo módulo começa com `prefix` (ex.: "Lenta")? */
    @Transactional(readOnly = true)
    public boolean hasModule(CharacterSheet sheet, CharacterTechnique t, String variationName, String prefix) {
        ArtTechnique cat = catalog(sheet, t);
        List<AssemblyItem> items = new ArrayList<>();
        for (AssemblyVariation v : variations(cat)) {
            if (variationName == null ? v == variations(cat).get(0) : variationName.equals(v.getName())) {
                if (v.getItems() != null) items.addAll(v.getItems());
            }
        }
        if (cat != null && cat.getExtras() != null) items.addAll(cat.getExtras());
        return items.stream().anyMatch(i -> i.getModule() != null && i.getModule().startsWith(prefix));
    }

    private static boolean attackCore(TechniqueCore c) {
        return c == null || c == TechniqueCore.STRIKE || c == TechniqueCore.CONTROL;
    }

    /** Técnica ACTIVE de núcleo Reação ou Preparação (uma por variação), com os itens da montagem e o gasto de SP. */
    public record CoreTechnique(CharacterTechnique technique, String variation, String name, int spCost,
                                List<AssemblyItem> items, boolean sketch) {}

    /** Técnicas ACTIVE da ficha cuja variação tem o núcleo dado (REACTION ou PREPARATION). */
    @Transactional(readOnly = true)
    public List<CoreTechnique> coreTechniques(CharacterSheet sheet, TechniqueCore core) {
        List<CoreTechnique> out = new ArrayList<>();
        for (CharacterTechnique t : techniqueRepository.findBySheetIdOrderByCreatedAt(sheet.getId())) {
            if (t.getKind() != MoveKind.TECHNIQUE || t.getState() != TechniqueState.ACTIVE) continue;
            ArtTechnique cat = catalog(sheet, t);
            for (AssemblyVariation v : variations(cat)) {
                if (v.getCore() != core) continue;
                List<AssemblyItem> items = new ArrayList<>(v.getItems() == null ? List.of() : v.getItems());
                if (cat.getExtras() != null) items.addAll(cat.getExtras());
                List<MasteryBonus> bonuses = t.getBonuses() == null ? List.of() : t.getBonuses();
                int sp = RollRules.spCost(cat.getSpCostBase(), count(bonuses, MasteryBonus.COST), physical(sheet));
                String name = variations(cat).size() > 1 ? t.getName() + " (" + v.getName() + ")" : t.getName();
                out.add(new CoreTechnique(t, variations(cat).size() > 1 ? v.getName() : null, name, sp, items,
                        t.getMastery() == Mastery.SKETCH));
            }
        }
        return out;
    }

    /** Núcleo da variação usada (Golpe, Controle, Reação, Preparação); null sem catálogo. */
    @Transactional(readOnly = true)
    public TechniqueCore core(CharacterSheet sheet, CharacterTechnique t, String variationName) {
        List<AssemblyVariation> vars = variations(catalog(sheet, t));
        return vars.stream().filter(v -> variationName == null ? v == vars.get(0) : variationName.equals(v.getName()))
                .findFirst().map(AssemblyVariation::getCore).orElse(null);
    }

    /** Itens de efeito (kind EFFECT) da variação usada + extras, para aplicar no acerto (§30). */
    @Transactional(readOnly = true)
    public List<AssemblyItem> effects(CharacterSheet sheet, CharacterTechnique t, String variationName) {
        ArtTechnique cat = catalog(sheet, t);
        List<AssemblyVariation> vars = variations(cat);
        List<AssemblyItem> items = new ArrayList<>();
        for (AssemblyVariation v : vars) {
            if (variationName == null ? v == vars.get(0) : variationName.equals(v.getName())) {
                if (v.getItems() != null) items.addAll(v.getItems());
            }
        }
        if (cat != null && cat.getExtras() != null) items.addAll(cat.getExtras());
        return items.stream().filter(i -> i.getKind() == AssemblyItemKind.EFFECT).toList();
    }

    /** Quantidade de "Impacto na Cabeça" da variação + bonificações POWER (perda de PQ por golpe, F6c). */
    @Transactional(readOnly = true)
    public int headImpactAndPower(CharacterSheet sheet, CharacterTechnique t, String variationName) {
        ArtTechnique cat = catalog(sheet, t);
        List<AssemblyVariation> vars = variations(cat);
        List<AssemblyItem> items = new ArrayList<>();
        for (AssemblyVariation v : vars) {
            if (variationName == null ? v == vars.get(0) : variationName.equals(v.getName())) {
                if (v.getItems() != null) items.addAll(v.getItems());
            }
        }
        if (cat != null && cat.getExtras() != null) items.addAll(cat.getExtras());
        int impact = items.stream().filter(i -> i.getModule() != null && i.getModule().startsWith("Impacto na Cabeça"))
                .mapToInt(AssemblyItem::getQty).sum();
        return impact + count(t.getBonuses() == null ? List.of() : t.getBonuses(), MasteryBonus.POWER);
    }

    @Transactional(readOnly = true)
    public Calc basic(CharacterSheet sheet, BasicAttack b, Opts o) {
        BaseRules.Profile base = base(sheet, o.baseId());
        List<RollPart> hit = new ArrayList<>(attackHit(sheet, o));
        if (b.hitPenalty != 0) hit.add(part("HIT", b.label, b.hitPenalty));
        hit.addAll(baseHit(base, o));
        hit.addAll(localized(o));
        hit.addAll(fatigueParts(sheet, physical(sheet)));
        List<RollPart> damage = new ArrayList<>();
        damage.add(part("DAMAGE", "FOR", RollRules.modOrZero(sheet.getStr())));
        if (b.flatDamage != 0) damage.add(part("DAMAGE", b.label, b.flatDamage));
        damage.addAll(baseDamage(base, o));
        int e = RollRules.strengthExtra(sheet.getStr(), false);
        return new Calc(RollSourceType.BASIC_ATTACK, null, b.label, null, b, 0, RollRules.CRIT_MARGIN, 0, hit,
                new RollRules.Dice(b.count * (1 + e), b.sides), damage);
    }

    @Transactional(readOnly = true)
    public Calc attribute(CharacterSheet sheet, Attribute a) {
        List<RollPart> hit = new ArrayList<>();
        hit.add(part("HIT", label(a), RollRules.modOrZero(sheet.getAttribute(a))));
        FatigueBand track = switch (a) {
            case STR, DEX, CON -> physical(sheet);
            case INT, CHA, MEN -> SheetRules.fatigueBand(sheet.getMentalFatigue());
            case TAL -> FatigueBand.RESTED;
        };
        boolean mental = a == Attribute.INT || a == Attribute.CHA || a == Attribute.MEN;
        hit.addAll(fatigueParts(sheet, track, mental ? "Desgaste Mental" : "Desgaste Físico"));
        return new Calc(RollSourceType.ATTRIBUTE_TEST, null, "Teste de " + label(a), null, null, 0,
                RollRules.CRIT_MARGIN, 0, hit, null, List.of());
    }

    public static RollProfileResponse toResponse(Calc c) {
        int hitFlat = c.hit().stream().mapToInt(RollPart::value).sum();
        String hitFormula = "1d20" + (hitFlat == 0 ? "" : RollRules.signed(hitFlat));
        RollProfileResponse.DamageDice dd = null;
        String damageFormula = null;
        if (c.dice() != null) {
            dd = new RollProfileResponse.DamageDice(c.dice().count(), c.dice().sides(), c.name());
            int flat = c.damage().stream().mapToInt(RollPart::value).sum();
            damageFormula = c.dice().text() + (flat == 0 ? "" : RollRules.signed(flat));
        }
        return new RollProfileResponse(c.type(), c.sourceId(), c.name(), c.variation(), c.basic(), c.spCost(),
                c.critMargin(), c.advantage(), c.hit(), dd, c.damage(), hitFormula, damageFormula);
    }

    public static String label(Attribute a) {
        return switch (a) {
            case STR -> "FOR";
            case DEX -> "DES";
            case CON -> "CON";
            case INT -> "INT";
            case CHA -> "CAR";
            case MEN -> "MEN";
            case TAL -> "TAL";
        };
    }

    // ---------- helpers ----------

    /** Parcelas de acerto da base: Precisão e Penalidade do tipo do golpe (§31). */
    private static List<RollPart> baseHit(BaseRules.Profile base, Opts o) {
        if (base == null) return List.of();
        List<RollPart> out = new ArrayList<>();
        int p = base.precision(o.attackType());
        if (p != 0) out.add(part("HIT", "Precisão (base: " + base.name() + ")", p));
        int pen = base.attackPenalty(o.attackType());
        if (pen != 0) out.add(part("HIT", "Penalidade (base: " + base.name() + ")", pen));
        return out;
    }

    private static List<RollPart> baseDamage(BaseRules.Profile base, Opts o) {
        int v = base == null ? 0 : base.strike(o.attackType());
        return v == 0 ? List.of() : List.of(part("DAMAGE", "Força do Golpe (base: " + base.name() + ")", v));
    }

    /** Base escolhida (BASE ACTIVE da ficha) com os itens do catálogo; 400 se inválida. */
    private BaseRules.Profile base(CharacterSheet sheet, UUID baseId) {
        if (baseId == null) return null;
        CharacterTechnique b = techniqueRepository.findById(baseId)
                .filter(x -> x.getSheet().getId().equals(sheet.getId()) && x.getKind() == MoveKind.BASE
                        && x.getState() == TechniqueState.ACTIVE)
                .orElseThrow(() -> new InvalidRollException("Base não encontrada ou inativa"));
        List<AssemblyItem> items = List.of();
        if (b.getCharacterArt() != null) {
            MartialArt art = martialArtRepository.findById(b.getCharacterArt().getArtId()).orElse(null);
            if (art != null && art.getBases() != null) {
                items = art.getBases().stream().filter(x -> b.getName().equals(x.getName())).findFirst()
                        .map(ArtBase::getItems).orElse(List.of());
            }
        }
        return BaseRules.profile(b.getName(), items, b.getMastery() == Mastery.SKETCH);
    }

    /** Perfil da base ativa para reações, resistência, Vontade e deslocamento; null sem base ou base inválida. */
    @Transactional(readOnly = true)
    public BaseRules.Profile baseProfile(CharacterSheet sheet, UUID baseId) {
        try {
            return base(sheet, baseId);
        } catch (InvalidRollException e) {
            return null;
        }
    }

    /** Redução de Dano do Calejamento da base ativa (valor do rótulo); 0 sem base ou base inválida. */
    @Transactional(readOnly = true)
    public int calejo(CharacterSheet sheet, UUID baseId) {
        BaseRules.Profile b = baseProfile(sheet, baseId);
        return b == null ? 0 : b.calejo();
    }

    private ArtTechnique catalog(CharacterSheet sheet, CharacterTechnique t) {
        if (t.getCharacterArt() == null) return null;
        List<MartialArt> pool = new ArrayList<>();
        if (t.getBorrowedFrom() != null) {
            pool.addAll(martialArtRepository.findByCampaignIdIsNull());
            if (!sheet.isNpcSheet()) {
                pool.addAll(martialArtRepository.findByCampaignId(sheet.getCampaignMember().getCampaign().getId()));
            }
            pool.removeIf(a -> !t.getBorrowedFrom().equals(a.getName()));
        } else {
            martialArtRepository.findById(t.getCharacterArt().getArtId()).ifPresent(pool::add);
        }
        return pool.stream().filter(a -> a.getTechniques() != null)
                .flatMap(a -> a.getTechniques().stream())
                .filter(x -> t.getName().equals(x.getName())).findFirst().orElse(null);
    }

    private static List<AssemblyVariation> variations(ArtTechnique cat) {
        return cat == null || cat.getVariations() == null ? List.of() : cat.getVariations();
    }

    private static int qty(Collection<AssemblyItem> items, String module) {
        return items.stream().filter(i -> module.equals(i.getModule())).mapToInt(AssemblyItem::getQty).sum();
    }

    private static int count(List<MasteryBonus> bonuses, MasteryBonus b) {
        return (int) bonuses.stream().filter(b::equals).count();
    }

    private static FatigueBand physical(CharacterSheet s) {
        return SheetRules.fatigueBand(s.getPhysicalFatigue());
    }

    private static List<RollPart> attackHit(CharacterSheet s, Opts o) {
        int mod = RollRules.modOrZero(s.getDex());
        return List.of(o.attackerDowned()
                ? part("HIT", "DES (atacante derrubado)", SheetRules.half(mod))
                : part("HIT", "DES", mod));
    }

    private static List<RollPart> localized(Opts o) {
        boolean has = o.region() != null && !o.region().isBlank();
        int pen = o.regionPenalty() == null ? 0 : o.regionPenalty();
        if (!has && pen == 0) return List.of();
        return List.of(part("HIT", "Ataque localizado" + (has ? " (" + o.region() + ")" : ""), pen));
    }

    private static List<RollPart> fatigueParts(CharacterSheet s, FatigueBand band) {
        return fatigueParts(s, band, "Desgaste Físico");
    }

    private static List<RollPart> fatigueParts(CharacterSheet s, FatigueBand band, String label) {
        int p = RollRules.fatiguePenalty(band);
        return p == 0 ? List.of() : List.of(part("HIT", label, p));
    }

    private static RollPart part(String kind, String source, int value) {
        return new RollPart(kind, source, value, null, false);
    }
}
