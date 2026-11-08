package com.tatakai.manager.roll.service;

import com.tatakai.manager.catalog.entity.ArtBase;
import com.tatakai.manager.catalog.entity.ArtTechnique;
import com.tatakai.manager.catalog.entity.AssemblyItem;
import com.tatakai.manager.catalog.entity.AssemblyItemKind;
import com.tatakai.manager.catalog.entity.AssemblyVariation;
import com.tatakai.manager.catalog.entity.MartialArt;
import com.tatakai.manager.catalog.entity.MartialType;
import com.tatakai.manager.catalog.entity.MoveKind;
import com.tatakai.manager.catalog.entity.TechniqueCore;
import com.tatakai.manager.catalog.repository.MartialArtRepository;
import com.tatakai.manager.catalog.service.WeaponCatalog;
import com.tatakai.manager.catalog.service.rules.ModuleRules;
import com.tatakai.manager.combat.entity.BasicAttack;
import com.tatakai.manager.combat.service.rules.BaseRules;
import com.tatakai.manager.combat.service.rules.WeaponRules;
import com.tatakai.manager.martial.entity.CharacterTechnique;
import com.tatakai.manager.martial.entity.Mastery;
import com.tatakai.manager.martial.entity.MasteryBonus;
import com.tatakai.manager.martial.entity.TechniqueState;
import com.tatakai.manager.martial.repository.CharacterTechniqueRepository;
import com.tatakai.manager.roll.dto.request.RollTestRequest;
import com.tatakai.manager.roll.dto.response.RollProfileResponse;
import com.tatakai.manager.roll.entity.RollMode;
import com.tatakai.manager.roll.entity.RollPart;
import com.tatakai.manager.roll.entity.RollSourceType;
import com.tatakai.manager.roll.exception.InvalidRollException;
import com.tatakai.manager.roll.service.rules.RollRules;
import com.tatakai.manager.sheet.entity.Attribute;
import com.tatakai.manager.sheet.entity.CharacterSheet;
import com.tatakai.manager.sheet.entity.FatigueBand;
import com.tatakai.manager.sheet.entity.InventoryItem;
import com.tatakai.manager.sheet.entity.ItemLocation;
import com.tatakai.manager.sheet.exception.SheetEntryNotFoundException;
import com.tatakai.manager.sheet.service.rules.SheetRules;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
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

    /**
     * Resultado do cálculo; dice já vem multiplicado por (1+E). `weapon`: arma usada (ataque com arma ou técnica
     * armada, §42); null = desarmado.
     */
    public record Calc(RollSourceType type, UUID sourceId, String name, String variation, BasicAttack basic,
                       int spCost, int critMargin, int advantage, List<RollPart> hit,
                       RollRules.Dice dice, List<RollPart> damage, WeaponCatalog.Def weapon) {

        public Calc(RollSourceType type, UUID sourceId, String name, String variation, BasicAttack basic, int spCost,
                    int critMargin, int advantage, List<RollPart> hit, RollRules.Dice dice, List<RollPart> damage) {
            this(type, sourceId, name, variation, basic, spCost, critMargin, advantage, hit, dice, damage, null);
        }

        /** Cópia com outro gasto, margem, vantagem e parcelas (mantém dados e arma). */
        public Calc with(int spCost, int critMargin, int advantage, List<RollPart> hit, List<RollPart> damage) {
            return new Calc(type, sourceId, name, variation, basic, spCost, critMargin, advantage, hit, dice, damage, weapon);
        }

        public Calc withDice(RollRules.Dice d) {
            return new Calc(type, sourceId, name, variation, basic, spCost, critMargin, advantage, hit, d, damage, weapon);
        }

        /** Arma de fogo e explosivos: o crítico não dobra os dados (Crítico frio). */
        public boolean coldCritical() {
            return weapon != null && (weapon.firearm() || weapon.area());
        }
    }

    /** Todo ataque básico (desarmado ou com arma) custa 1 SP; técnicas têm custo próprio. */
    public static final int BASIC_SP_COST = 1;

    /** Catálogo estático de armas (só leitura do classpath). */
    private final WeaponCatalog weapons = new WeaponCatalog();
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
            if (cat != null && cat.getWeaponClass() != null && wielded(sheet, cat) == null) continue; // sem a arma
            if (vars.isEmpty()) {
                out.add(technique(sheet, t, null, Opts.NONE));
            } else {
                for (AssemblyVariation v : vars) {
                    if (!attackCore(v.getCore())) continue; // Reação e Preparação não são ataques
                    out.add(technique(sheet, t, v.getName(), Opts.NONE));
                }
            }
        }
        for (BasicAttack b : BasicAttack.values()) if (b.unarmed()) out.add(basic(sheet, b, Opts.NONE));
        for (InventoryItem i : inHand(sheet)) {
            WeaponCatalog.Def w = weapons.find(i.getWeaponKey()).orElse(null);
            if (w == null || w.augment() || i.isBroken() || w.dice() == null && !w.smoke()) continue;
            out.add(weapon(sheet, i, Opts.NONE, false, false));
            if (!w.thrownWeapon() && !w.shooter() && !w.throwOnly()) out.add(weapon(sheet, i, Opts.NONE, true, false));
            if (w.light()) out.add(weapon(sheet, i, Opts.NONE, false, true));
        }
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
                if (req.basicAttack() == BasicAttack.ARMA) yield weaponOf(sheet, req.sourceId(), req.variation(), o);
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
        InventoryItem armed = cat != null && cat.getWeaponClass() != null ? wielded(sheet, cat) : null;
        if (cat != null && cat.getWeaponClass() != null && armed == null) {
            String single = ModuleRules.singleWeapon(items);
            throw new InvalidRollException(single != null && !single.isEmpty()
                    ? "A técnica exige a arma única " + single + " em mãos"
                    : "A técnica exige uma arma da classe " + cat.getWeaponClass() + " em mãos");
        }
        WeaponCatalog.Def weapon = armed == null ? null : weapons.find(armed.getWeaponKey()).orElse(null);

        ModuleRules.AltAttribute alt = ModuleRules.altAttribute(items);
        int weight = SheetRules.weightMod(sheet.getWeightKg());
        List<RollPart> hit = new ArrayList<>(alt != null && alt.test() && alt.attribute() != Attribute.DEX
                ? List.of(part("HIT", label(alt.attribute()) + " (Atributo Alternativo)",
                        o.attackerDowned() ? SheetRules.half(RollRules.modOrZero(sheet.getAttribute(alt.attribute())))
                                : RollRules.modOrZero(sheet.getAttribute(alt.attribute()))))
                : attackHit(sheet, o));
        if (precision > 0) hit.add(part("HIT", "Precisão (variação)", precision));
        int massHit = ModuleRules.massHit(items, weight);
        if (massHit > 0) hit.add(part("HIT", "Massa", massHit));
        if (hitBonus > 0) hit.add(part("HIT", "Bonificação HIT", hitBonus));
        hit.addAll(baseHit(base, o));
        hit.addAll(localized(o));
        hit.addAll(fatigueParts(sheet, physical(sheet)));

        List<RollPart> damage = new ArrayList<>();
        Attribute dmgAttr = alt != null && !alt.test() ? alt.attribute() : Attribute.STR;
        if (weapon != null) {
            damage.addAll(weaponAttribute(sheet, weapon, false));
        } else {
            damage.add(part("DAMAGE", dmgAttr == Attribute.STR ? "FOR" : label(dmgAttr) + " (Atributo Alternativo)",
                    RollRules.modOrZero(sheet.getAttribute(dmgAttr))));
            damage.addAll(augment(sheet));
        }
        if (damageBonus > 0) damage.add(part("DAMAGE", "Bonificação DAMAGE", 2 * damageBonus));
        int massDamage = ModuleRules.massDamage(items, weight);
        if (massDamage > 0) damage.add(part("DAMAGE", "Massa", massDamage));
        damage.addAll(baseDamage(base, o));

        // Controle só causa dano com Dado Maior, e o 1º degrau é 1d4
        boolean control = variation != null && variation.getCore() == TechniqueCore.CONTROL;
        int e = RollRules.strengthExtra(sheet.getStr(), apex);
        RollRules.Dice dice;
        if (weapon != null) {
            // Dano Armado: dado da arma (Golpe Pesado sobe os dados) + Dado Adicional pelo Dado Maior
            dice = WeaponRules.heavier(weaponDice(sheet, armed, weapon), qty(items, "Golpe Pesado"));
            RollRules.Dice extra = WeaponRules.extraDie(degree);
            if (extra != null) dice = dice.with(extra);
        } else {
            dice = control && degree == 0 ? null : RollRules.diceScale(control ? degree - 1 : degree).scaled(1 + e);
        }
        if (dice == null) damage = new ArrayList<>();
        int sp = RollRules.spCost(cat == null ? null : cat.getSpCostBase(), count(bonuses, MasteryBonus.COST),
                physical(sheet)) + (weapon == null ? 0 : weapon.load());
        if (t.getCharacterArt() != null && "Muay Thai".equalsIgnoreCase(t.getCharacterArt().getArtName())) {
            sp = Math.max(1, sp - 2); // Preparando a Arma: técnicas de Muay Thai custam −2 SP (mín. 1)
        }
        int adv = t.getMastery() == Mastery.SKETCH ? -1 : 0;
        return new Calc(RollSourceType.TECHNIQUE, t.getId(), t.getName(), variation == null ? null : variation.getName(),
                null, sp, RollRules.CRIT_MARGIN, adv, hit, dice, damage, weapon);
    }

    /** A técnica é armada (exige uma classe de arma)? */
    @Transactional(readOnly = true)
    public boolean isArmed(CharacterSheet sheet, CharacterTechnique t) {
        ArtTechnique cat = catalog(sheet, t);
        return cat != null && cat.getWeaponClass() != null;
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
                if (t.getCharacterArt() != null && "Muay Thai".equalsIgnoreCase(t.getCharacterArt().getArtName())) {
                    sp = Math.max(1, sp - 2);
                }
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
        return items(sheet, t, variationName).stream().filter(i -> i.getKind() == AssemblyItemKind.EFFECT).toList();
    }

    /** Todos os itens da variação usada (a primeira, se `variationName` for null) + extras da técnica. */
    @Transactional(readOnly = true)
    public List<AssemblyItem> items(CharacterSheet sheet, CharacterTechnique t, String variationName) {
        return itemsOf(catalog(sheet, t), variationName);
    }

    private static List<AssemblyItem> itemsOf(ArtTechnique cat, String variationName) {
        List<AssemblyVariation> vars = variations(cat);
        List<AssemblyItem> items = new ArrayList<>();
        for (AssemblyVariation v : vars) {
            if (variationName == null ? v == vars.get(0) : variationName.equals(v.getName())) {
                if (v.getItems() != null) items.addAll(v.getItems());
            }
        }
        if (cat != null && cat.getExtras() != null) items.addAll(cat.getExtras());
        return items;
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
        if (!b.unarmed()) throw new InvalidRollException("Escolha a arma em mãos");
        BaseRules.Profile base = base(sheet, o.baseId());
        List<RollPart> hit = new ArrayList<>(attackHit(sheet, o));
        if (b.hitPenalty != 0) hit.add(part("HIT", b.label, b.hitPenalty));
        hit.addAll(baseHit(base, o));
        hit.addAll(localized(o));
        hit.addAll(fatigueParts(sheet, physical(sheet)));
        if (!b.dealsDamage()) {
            return new Calc(RollSourceType.BASIC_ATTACK, null, b.label, null, b, BASIC_SP_COST, RollRules.CRIT_MARGIN, 0, hit,
                    null, List.of());
        }
        List<RollPart> damage = new ArrayList<>();
        if (!b.noStrength()) damage.add(part("DAMAGE", "FOR", RollRules.modOrZero(sheet.getStr())));
        if (b.flatDamage != 0) damage.add(part("DAMAGE", b.label, b.flatDamage));
        if (b == BasicAttack.PISAO) damage.add(part("DAMAGE", "Peso", SheetRules.weightMod(sheet.getWeightKg())));
        if (b == BasicAttack.SOCO) damage.addAll(augment(sheet)); // soqueira/tufa endurecem o punho
        damage.addAll(baseDamage(base, o));
        int e = b.noStrength() ? 0 : RollRules.strengthExtra(sheet.getStr(), false);
        return new Calc(RollSourceType.BASIC_ATTACK, null, b.label, null, b, BASIC_SP_COST, RollRules.CRIT_MARGIN, 0, hit,
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
        return toResponse(c, List.of());
    }

    public static RollProfileResponse toResponse(Calc c, List<String> modules) {
        int hitFlat = c.hit().stream().mapToInt(RollPart::value).sum();
        String hitFormula = "1d20" + (hitFlat == 0 ? "" : RollRules.signed(hitFlat));
        RollProfileResponse.DamageDice dd = null;
        String damageFormula = null;
        if (c.dice() != null) {
            dd = new RollProfileResponse.DamageDice(c.dice().count(), c.dice().sides(), c.name(), c.dice().text());
            int flat = c.damage().stream().mapToInt(RollPart::value).sum();
            damageFormula = c.dice().text() + (flat == 0 ? "" : RollRules.signed(flat));
        }
        return new RollProfileResponse(c.type(), c.sourceId(), c.name(), c.variation(), c.basic(), c.spCost(),
                c.critMargin(), c.advantage(), c.hit(), dd, c.damage(), hitFormula, damageFormula, modules,
                c.weapon() == null ? null : c.weapon().key());
    }

    // ---------- armas (§42) ----------

    /** Itens em mãos (inteiros, não quebrados). */
    public static List<InventoryItem> inHand(CharacterSheet sheet) {
        return sheet.getInventory() == null ? List.of() : sheet.getInventory().stream()
                .filter(i -> i.getLocation() == com.tatakai.manager.sheet.entity.ItemLocation.HAND && !i.isBroken()).toList();
    }

    private static String fold(String s) {
        return s == null ? "" : java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).trim();
    }

    /** Arma em mãos que serve à técnica armada: mesma classe ("Versátil": mesmo tipo marcial; "Arma Única": nome/chave). */
    private InventoryItem wielded(CharacterSheet sheet, ArtTechnique cat) {
        List<AssemblyItem> all = new ArrayList<>();
        if (cat.getExtras() != null) all.addAll(cat.getExtras());
        for (AssemblyVariation v : variations(cat)) if (v.getItems() != null) all.addAll(v.getItems());
        String single = ModuleRules.singleWeapon(all);
        String singleFold = single == null ? "" : fold(single);
        boolean versatile = ModuleRules.has(all, "Abrangência Versátil");
        WeaponCatalog.Def ref = weapons.all().stream().filter(w -> cat.getWeaponClass().equalsIgnoreCase(w.weaponClass()))
                .findFirst().orElse(null);
        for (InventoryItem i : inHand(sheet)) {
            WeaponCatalog.Def w = weapons.find(i.getWeaponKey()).orElse(null);
            if (w == null || w.augment()) continue;
            if (!singleFold.isEmpty() && !fold(i.getName()).equals(singleFold)
                    && !fold(w.name()).equals(singleFold) && !fold(w.key()).equals(singleFold)) {
                continue;
            }
            if (cat.getWeaponClass().equalsIgnoreCase(w.weaponClass())) return i;
            if (versatile && ref != null && ref.martialType().equals(w.martialType())) return i;
        }
        return null;
    }

    /** Dados da arma: Empunhadura Variável com duas mãos, danificada −1 dado, arremesso com Escala pela DES. */
    private static RollRules.Dice weaponDice(CharacterSheet sheet, InventoryItem item, WeaponCatalog.Def w) {
        RollRules.Dice d = RollRules.Dice.parse(item.isTwoHanded() && w.variable() ? w.diceTwoHands() : w.dice());
        if (d == null) return null;
        if (item.isDamaged()) d = new RollRules.Dice(Math.max(1, d.count() - 1), d.sides(), d.plus());
        if (w.thrownWeapon() && !w.area()) d = d.scaled(1 + RollRules.strengthExtra(sheet.getDex(), false));
        return d;
    }

    /**
     * Atributo do dano pela categoria: branca/improvisada = FOR (Leve: o melhor de FOR e DES), arremesso e disparo =
     * DES, fogo e explosivos = nada. Golpe Rápido de arma Leve: só o dado.
     */
    private static List<RollPart> weaponAttribute(CharacterSheet sheet, WeaponCatalog.Def w, boolean quick) {
        if (quick || w.firearm() || w.area()) return List.of();
        int str = RollRules.modOrZero(sheet.getStr());
        int dex = RollRules.modOrZero(sheet.getDex());
        if (w.thrownWeapon() || w.shooter()) return List.of(part("DAMAGE", "DES", dex));
        if (w.light() && dex > str) return List.of(part("DAMAGE", "DES (Leve)", dex));
        return List.of(part("DAMAGE", "FOR", str));
    }

    /** Arma de aumento em mãos (soqueira, tufa): bônus fixo no golpe desarmado. */
    private List<RollPart> augment(CharacterSheet sheet) {
        for (InventoryItem i : inHand(sheet)) {
            WeaponCatalog.Def w = weapons.find(i.getWeaponKey()).orElse(null);
            if (w != null && w.augment() && w.bonus() != null) return List.of(part("DAMAGE", i.getName(), w.bonus()));
        }
        return List.of();
    }

    /**
     * Ataque com a arma em mãos (§42): acerto 1d20 + DES; dano pela categoria. `thrown`: arremesso improvisado de arma
     * corpo a corpo (desvantagem, Pequena −1); `quick`: Golpe Rápido de arma Leve (só o dado).
     */
    @Transactional(readOnly = true)
    public Calc weapon(CharacterSheet sheet, InventoryItem item, Opts o, boolean thrown, boolean quick) {
        WeaponCatalog.Def w = weapons.find(item.getWeaponKey())
                .orElseThrow(() -> new InvalidRollException("O item não é uma arma do catálogo"));
        if (item.isBroken()) throw new InvalidRollException(item.getName() + " está quebrada");
        List<RollPart> hit = new ArrayList<>(attackHit(sheet, o));
        int adv = 0;
        if (thrown) {
            if (w.small()) hit.add(part("HIT", "Arremesso (Pequena)", -1));
            else adv--;
        }
        if (w.heavyStr() != null && sheet.getStr() < w.heavyStr()) {
            adv--;
            hit.add(part("HIT", "Pesada: Força " + w.heavyStr() + " (desvantagem)", 0));
        }
        hit.addAll(localized(o));
        hit.addAll(fatigueParts(sheet, physical(sheet)));
        List<RollPart> damage = new ArrayList<>(weaponAttribute(sheet, w, quick));
        String name = item.getName() + (thrown ? " (arremesso)" : quick ? " (Golpe Rápido)" : "");
        return new Calc(RollSourceType.BASIC_ATTACK, item.getId(), name, thrown ? "THROWN" : quick ? "QUICK" : null,
                BasicAttack.ARMA, BASIC_SP_COST, RollRules.CRIT_MARGIN, adv, hit, weaponDice(sheet, item, w), damage, w);
    }

    /** Ataque com o item em mãos `itemId`; variação "THROWN" (arremesso) ou "QUICK" (Golpe Rápido). */
    @Transactional(readOnly = true)
    public Calc weaponOf(CharacterSheet sheet, UUID itemId, String variation, Opts o) {
        InventoryItem item = inHand(sheet).stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new InvalidRollException("A arma precisa estar em mãos"));
        return weapon(sheet, item, o, "THROWN".equals(variation), "QUICK".equals(variation));
    }

    public Optional<WeaponCatalog.Def> weaponDef(InventoryItem item) {
        return item == null ? Optional.empty() : weapons.find(item.getWeaponKey());
    }

    /** Nomes dos módulos da técnica (variação dada) para a tela; vazio se não for técnica. */
    @Transactional(readOnly = true)
    public List<String> moduleNames(CharacterSheet sheet, UUID techniqueId, String variationName) {
        if (techniqueId == null) return List.of();
        return techniqueRepository.findById(techniqueId)
                .map(t -> items(sheet, t, variationName).stream().map(AssemblyItem::getModule)
                        .filter(java.util.Objects::nonNull).distinct().toList())
                .orElse(List.of());
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
        if (b.getCharacterArt() == null && b.getBaseAssembly() != null && b.getBaseAssembly().getItems() != null) {
            items = b.getBaseAssembly().getItems(); // avulsa montada
        }
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
        if (t.getCharacterArt() == null) return t.getTechniqueAssembly(); // avulsa montada (ou null)
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
