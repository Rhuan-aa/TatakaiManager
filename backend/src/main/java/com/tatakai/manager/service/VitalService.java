package com.tatakai.manager.service;

import com.tatakai.manager.dto.response.RollMessage;
import com.tatakai.manager.entity.ActivityCategory;
import com.tatakai.manager.entity.ActorRole;
import com.tatakai.manager.entity.CharacterSheet;
import com.tatakai.manager.entity.CombatClassification;
import com.tatakai.manager.entity.CombatCondition;
import com.tatakai.manager.entity.CombatEvent;
import com.tatakai.manager.entity.CombatEventType;
import com.tatakai.manager.entity.ConditionExpiry;
import com.tatakai.manager.entity.Encounter;
import com.tatakai.manager.entity.EncounterParticipant;
import com.tatakai.manager.entity.Hysteria;
import com.tatakai.manager.entity.Roll;
import com.tatakai.manager.entity.RollPart;
import com.tatakai.manager.entity.RollSourceType;
import com.tatakai.manager.exception.InvalidCombatException;
import com.tatakai.manager.repository.CombatConditionRepository;
import com.tatakai.manager.repository.CombatEventRepository;
import com.tatakai.manager.repository.EncounterParticipantRepository;
import com.tatakai.manager.service.RollProfileService.Calc;
import com.tatakai.manager.service.rules.CombatRules;
import com.tatakai.manager.service.rules.RollRules;
import com.tatakai.manager.service.rules.BaseRules;
import com.tatakai.manager.service.rules.EnvironmentRules;
import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.service.rules.SheetRules;
import com.tatakai.manager.entity.PermanentInjury;
import com.tatakai.manager.entity.InjuryType;
import com.tatakai.manager.entity.InjurySeverity;
import com.tatakai.manager.service.rules.VitalRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ponto único de alteração de HP/SP/PQ em combate (F6c): dispara desmaios, testes de Vontade/CON,
 * Adrenalina de crise e demais autômatos. Não depende de CombatService nem de AttackService.
 */
@Service
@Transactional
public class VitalService {

    public static final String UNCONSCIOUS = "INCONSCIENTE";
    public static final String DYING = "BEIRA_DA_MORTE";
    public static final String STABLE = "ESTABILIZADO";
    public static final String DEAD = "MORTO";
    public static final String EXHAUSTED = "EXAUSTO";
    public static final String DISSOCIATED = "DISSOCIADO";
    public static final String DISORIENTED = "DESORIENTADO";
    public static final String BURNING = "QUEIMANDO";
    public static final String FREEZING = "CONGELANDO";
    public static final String POISONED = "ENVENENADO";
    public static final String SUFFOCATING = "SUFOCANDO";

    /** Quem causa a alteração (para log de ficha/rolagens) e o motivo; critical = golpe crítico. */
    public record Ctx(UUID userId, ActorRole role, String reason, boolean critical) {
        public static Ctx of(UUID userId, ActorRole role, String reason) {
            return new Ctx(userId, role, reason, false);
        }

        public Ctx withCritical(boolean c) {
            return new Ctx(userId, role, reason, c);
        }
    }

    private final EncounterParticipantRepository participants;
    private final CombatConditionRepository conditions;
    private final CombatEventRepository timeline;
    private final SheetActivityLogService activityLog;
    private final RollService rolls;
    private final ConditionCatalog catalog;
    private final RollProfileService profiles;

    public VitalService(EncounterParticipantRepository participants, CombatConditionRepository conditions,
                        CombatEventRepository timeline, SheetActivityLogService activityLog, RollService rolls,
                        ConditionCatalog catalog, RollProfileService profiles) {
        this.profiles = profiles;
        this.participants = participants;
        this.conditions = conditions;
        this.timeline = timeline;
        this.activityLog = activityLog;
        this.rolls = rolls;
        this.catalog = catalog;
    }

    // ---------- util ----------

    private void log(Encounter e, UUID participantId, CombatEventType type, String text) {
        timeline.save(CombatEvent.builder().encounterId(e.getId()).round(e.getRound()).turnCounter(e.getTurnCounter())
                .participantId(participantId).type(type).text(text.length() > 500 ? text.substring(0, 500) : text).build());
    }

    private List<CombatCondition> conds(EncounterParticipant p) {
        return new ArrayList<>(conditions.findByParticipantIdIn(List.of(p.getId())));
    }

    private static boolean has(List<CombatCondition> l, String key) {
        return l.stream().anyMatch(c -> key.equals(c.getConditionKey()));
    }

    private String name(String key) {
        return catalog.find(key).map(ConditionCatalog.Def::name).orElse(key);
    }

    private void record(CharacterSheet s, Ctx c, String action, String field, int before, int after) {
        activityLog.record(s, c.userId(), c.role(), ActivityCategory.RESOURCE, action, field, before, after, null,
                c.reason());
    }

    public boolean hasCondition(EncounterParticipant p, String key) {
        return has(conds(p), key);
    }

    /** Aplica (ou renova) uma condição e registra na linha do tempo. */
    public void addCondition(Encounter e, EncounterParticipant p, String key, ConditionExpiry expiry, Integer turns,
                             String note) {
        CombatCondition c = conds(p).stream().filter(x -> key.equals(x.getConditionKey())).findFirst()
                .orElseGet(() -> CombatCondition.builder().participantId(p.getId()).conditionKey(key).build());
        c.setExpiresAt(expiry);
        c.setAppliedTurn(e.getTurnCounter());
        c.setTurnsRemaining(turns);
        c.setNote(note);
        conditions.save(c);
        log(e, p.getId(), CombatEventType.CONDITION_ADDED,
                p.getName() + ": " + name(key) + (note == null ? "" : " (" + note + ")") + " aplicada");
    }

    /** Efeito de módulo (§30): duração dada (null = padrão do catálogo) e quem aplicou (Marcar usa a origem). */
    public void addEffectCondition(Encounter e, EncounterParticipant p, String key, ConditionExpiry expiry, String note, UUID sourceId) {
        ConditionExpiry ex = expiry != null ? expiry
                : catalog.find(key).map(ConditionCatalog.Def::defaultExpiresAt).orElse(ConditionExpiry.MANUAL);
        addCondition(e, p, key, ex, ex == ConditionExpiry.COUNTER ? 1 : null, note);
        conds(p).stream().filter(c -> key.equals(c.getConditionKey())).findFirst().ifPresent(c -> {
            c.setSourceParticipantId(sourceId);
            conditions.save(c);
        });
    }

    public void removeCondition(Encounter e, EncounterParticipant p, String key) {
        for (CombatCondition c : conds(p)) {
            if (key.equals(c.getConditionKey())) {
                conditions.delete(c);
                log(e, p.getId(), CombatEventType.CONDITION_REMOVED, p.getName() + " não está mais " + name(key));
            }
        }
    }

    // ---------- testes automáticos ----------

    /** Base ativa do participante (§31); null sem base. */
    public BaseRules.Profile baseOf(EncounterParticipant p, CharacterSheet s) {
        return p.getActiveBaseId() == null ? null : profiles.baseProfile(s, p.getActiveBaseId());
    }

    /** Teste de Vontade (1d20 + mod. MEN, Fluxo/Muralha/Desgaste) contra `nd`; registra na linha do tempo. */
    public boolean willTest(Encounter e, EncounterParticipant p, CharacterSheet s, int nd, String label, Ctx c) {
        return test(e, p, s, true, nd, label, c);
    }

    private boolean test(Encounter e, EncounterParticipant p, CharacterSheet s, boolean mental, int nd, String label,
                         Ctx c) {
        List<RollPart> hit = new ArrayList<>();
        hit.add(new RollPart("HIT", mental ? "MEN" : "CON",
                RollRules.modOrZero(mental ? s.getMen() : s.getCon()), null, false));
        int pen = CombatRules.fatiguePenalty(mental ? s.getMentalFatigue() : s.getPhysicalFatigue());
        if (pen > 0) hit.add(new RollPart("HIT", mental ? "Desgaste Mental" : "Desgaste Físico", -pen, null, false));
        BaseRules.Profile base = mental ? baseOf(p, s) : null;
        if (base != null && base.calm() != 0) {
            hit.add(new RollPart("HIT", "Calma (base: " + base.name() + ")", base.calm(), null, false));
        }
        VitalRules.Tier tier = VitalRules.tier(p.getAdrenaline());
        if (mental && VitalRules.activeHysteria(p.getAdrenaline(), p.getHysteria()) == Hysteria.WALL) {
            hit.add(new RollPart("HIT", "Muralha", 4, null, false));
        }
        int adv = mental && tier != VitalRules.Tier.NONE ? 1 : 0;
        if (!mental && hasCondition(p, POISONED)) adv--; // Envenenado: desvantagem em CON
        Calc calc = new Calc(RollSourceType.ATTRIBUTE_TEST, null, mental ? "Vontade" : "Constituição", null, null, 0,
                RollRules.CRIT_MARGIN, 0, hit, null, List.of());
        RollMessage m = rolls.rollCalc(s, e.getCampaignId(), calc, adv,
                (mental ? "Vontade" : "Salvaguarda de morte") + " (" + label + "): " + p.getName() + " — ND " + nd,
                c.userId(), c.role());
        boolean ok = m.total() >= nd;
        log(e, p.getId(), CombatEventType.ACTION, p.getName() + ": " + (mental ? "Vontade" : "Salvaguarda de morte")
                + " (" + label + ") " + m.total() + " vs ND " + nd + " — " + (ok ? "sucesso" : "falha"));
        return ok;
    }

    // ---------- HP ----------

    public void applyHp(Encounter e, EncounterParticipant p, CharacterSheet s, int delta, Ctx c) {
        if (delta == 0) return;
        int before = s.getCurrentHp();
        int after = delta < 0 ? Math.max(0, before + delta) : Math.min(Math.max(s.getHpMax(), before), before + delta);
        if (after != before) {
            s.setCurrentHp(after);
            record(s, c, delta < 0 ? "COMBAT_DAMAGE" : "COMBAT_HEAL", "currentHp", before, after);
        }
        List<CombatCondition> mine = conds(p);
        if (delta > 0) {
            if (before == 0 && after > 0) {
                removeCondition(e, p, DYING);
                removeCondition(e, p, STABLE);
                p.setDeathSuccesses(0);
                p.setDeathFailures(0);
                for (CombatCondition k : mine) {
                    if (UNCONSCIOUS.equals(k.getConditionKey()) && "HP".equals(k.getNote())) {
                        conditions.delete(k);
                        log(e, p.getId(), CombatEventType.CONDITION_REMOVED, p.getName() + " voltou a si");
                    }
                }
            }
            return;
        }
        p.setDamageThisTurn(p.getDamageThisTurn() + (before - after));
        if (has(mine, DEAD)) return;
        if (has(mine, DYING)) {
            deathFailure(e, p, c.critical() ? 2 : 1);
        } else if (before > 0 && after == 0) {
            hpZero(e, p, s, -delta - before);
        }
        if (VitalRules.crisisByDamage(p.getDamageThisTurn(), s.getHpMax())
                || (before > 0 && VitalRules.crisisByLowHp(after, s.getHpMax()))) {
            crisis(e, p);
        }
        if (before > 0 && after == 0) saveTheNext(e, p);
    }

    private void hpZero(Encounter e, EncounterParticipant p, CharacterSheet s, int excess) {
        addCondition(e, p, UNCONSCIOUS, ConditionExpiry.MANUAL, null, "HP");
        if (p.getClassification() == CombatClassification.LETHAL) {
            if (VitalRules.massiveDamage(excess, s.getHpMax())) {
                addCondition(e, p, DEAD, ConditionExpiry.MANUAL, null, null);
                log(e, p.getId(), CombatEventType.ACTION, p.getName() + " morreu (dano massivo)");
            } else {
                p.setDeathSuccesses(0);
                p.setDeathFailures(0);
                addCondition(e, p, DYING, ConditionExpiry.MANUAL, null, null);
            }
        } else {
            log(e, p.getId(), CombatEventType.ACTION, p.getName() + " ficou inconsciente (HP 0)");
        }
    }

    private void deathFailure(Encounter e, EncounterParticipant p, int n) {
        p.setDeathFailures(p.getDeathFailures() + n);
        log(e, p.getId(), CombatEventType.ACTION, p.getName() + " falha na morte (" + p.getDeathFailures() + "/3)");
        if (p.getDeathFailures() >= 3) {
            removeCondition(e, p, DYING);
            addCondition(e, p, DEAD, ConditionExpiry.MANUAL, null, null);
        }
    }

    private void deathSave(Encounter e, EncounterParticipant p, CharacterSheet s, Ctx c) {
        if (test(e, p, s, false, VitalRules.DEATH_SAVE_ND, "Beira da Morte", c)) {
            p.setDeathSuccesses(p.getDeathSuccesses() + 1);
            if (p.getDeathSuccesses() >= 3) {
                removeCondition(e, p, DYING);
                addCondition(e, p, STABLE, ConditionExpiry.MANUAL, null, null);
            }
        } else {
            deathFailure(e, p, 1);
        }
    }

    // ---------- SP ----------

    public void applySp(Encounter e, EncounterParticipant p, CharacterSheet s, int delta, Ctx c) {
        if (delta == 0) return;
        if (delta > 0 && VitalRules.collapse(s.getPhysicalFatigue())) return; // Colapso: SP não recupera
        int before = s.getCurrentSp();
        int after = delta < 0 ? Math.max(0, before + delta) : Math.min(Math.max(s.getSpMax(), before), before + delta);
        if (after == before) return;
        s.setCurrentSp(after);
        record(s, c, "COMBAT_SP", "currentSp", before, after);
        if (after > 0 && p.getExhaustionNd() != null) {
            p.setExhaustionNd(null);
            removeCondition(e, p, EXHAUSTED);
        }
        if (before > 0 && after == 0) spZero(e, p, s, c);
    }

    private void spZero(Encounter e, EncounterParticipant p, CharacterSheet s, Ctx c) {
        boolean dissociated = VitalRules.dissociated(s.getMentalFatigue());
        if (hasCondition(p, SUFFOCATING)) { // Sufocando: SP 0 → Inconsciente sem o Teste de Vontade
            log(e, p.getId(), CombatEventType.ACTION, p.getName() + " desmaia sem ar");
            unconsciousBySp(e, p);
            return;
        }
        if (dissociated || !test(e, p, s, true, VitalRules.SP_ZERO_ND, "SP 0", c)) {
            unconsciousBySp(e, p);
        } else {
            p.setExhaustionNd(VitalRules.EXHAUSTION_START_ND);
            addCondition(e, p, EXHAUSTED, ConditionExpiry.MANUAL, null, null);
        }
    }

    private void unconsciousBySp(Encounter e, EncounterParticipant p) {
        p.setExhaustionNd(null);
        removeCondition(e, p, EXHAUSTED);
        addCondition(e, p, UNCONSCIOUS, ConditionExpiry.COUNTER, 3, "SP");
    }

    private void exhaustionTest(Encounter e, EncounterParticipant p, CharacterSheet s, Ctx c) {
        int nd = p.getExhaustionNd();
        p.setExhaustionNd(nd + 2);
        if (!test(e, p, s, true, nd, "Exaustão", c)) unconsciousBySp(e, p);
    }

    // ---------- PQ ----------

    public void applyKp(Encounter e, EncounterParticipant p, CharacterSheet s, int delta, Ctx c) {
        if (delta == 0) return;
        int before = s.getCurrentKp();
        int after = delta < 0 ? Math.max(0, before + delta) : Math.min(Math.max(s.getKpMax(), before), before + delta);
        if (after == before) return;
        s.setCurrentKp(after);
        record(s, c, "COMBAT_KP", "currentKp", before, after);
        if (before > 0 && after == 0) kpZero(e, p, s, c);
    }

    private void kpZero(Encounter e, EncounterParticipant p, CharacterSheet s, Ctx c) {
        int nd = VitalRules.kpZeroNd(p.getKpZeroings());
        p.setKpZeroings(p.getKpZeroings() + 1);
        boolean dissociated = VitalRules.dissociated(s.getMentalFatigue());
        if (!dissociated && test(e, p, s, true, nd, "PQ 0", c)) {
            int before = s.getCurrentKp();
            s.setCurrentKp(2);
            record(s, c, "COMBAT_KP", "currentKp", before, 2);
        } else {
            s.setKnockoutSequel(true); // só pesa se a campanha usar a Sequela de Nocaute (aplicada no próximo combate)
            Roll r = rolls.rollInternal(s, "1d4", "Duração do desmaio por PQ: " + p.getName(), c.userId(), c.role());
            addCondition(e, p, UNCONSCIOUS, ConditionExpiry.COUNTER, r.getTotal() + 2, "PQ");
        }
    }

    /** Despertar: chamado quando uma INCONSCIENTE (SP/PQ) expira. */
    public void onWake(Encounter e, EncounterParticipant p, CharacterSheet s, CombatCondition cond, Ctx c) {
        if (!UNCONSCIOUS.equals(cond.getConditionKey())) return;
        if ("SP".equals(cond.getNote())) {
            int before = s.getCurrentSp();
            s.setCurrentSp(1);
            record(s, c, "COMBAT_SP", "currentSp", before, 1);
        } else if ("PQ".equals(cond.getNote())) {
            int before = s.getCurrentKp();
            s.setCurrentKp(1);
            record(s, c, "COMBAT_KP", "currentKp", before, 1);
            addCondition(e, p, DISORIENTED, ConditionExpiry.END_OF_TARGET_TURN, null, null);
        }
    }

    // ---------- Adrenalina ----------

    public void setAdrenaline(EncounterParticipant p, int value) {
        int v = VitalRules.clampAdrenaline(value);
        p.setAdrenaline(v);
        if (v < VitalRules.MAX_ADRENALINE) {
            p.setHysteria(null);
            p.setRoundsAtApex(0);
        }
    }

    private void crisis(Encounter e, EncounterParticipant p) {
        if (p.getAdrenaline() >= VitalRules.MAX_ADRENALINE) return;
        setAdrenaline(p, VitalRules.MAX_ADRENALINE);
        log(e, p.getId(), CombatEventType.ACTION, p.getName() + " entra em crise: Adrenalina no Ápice");
    }

    private void saveTheNext(Encounter e, EncounterParticipant fallen) {
        for (EncounterParticipant a : participants.findByEncounterIdOrderByOrderIndex(e.getId())) {
            if (a.getId().equals(fallen.getId()) || a.getTeam() != fallen.getTeam() || a.isSaveNextUsed()) continue;
            a.setSaveNextUsed(true);
            setAdrenaline(a, a.getAdrenaline() + 5);
            log(e, a.getId(), CombatEventType.ACTION, a.getName() + " ganha +5 de Adrenalina (Salvar o Próximo)");
        }
    }

    public void setHysteria(Encounter e, EncounterParticipant p, Hysteria h) {
        if (VitalRules.tier(p.getAdrenaline()) != VitalRules.Tier.APEX) {
            throw new InvalidCombatException("A Histeria só pode ser escolhida no Ápice da Adrenalina");
        }
        p.setHysteria(h);
        log(e, p.getId(), CombatEventType.ACTION, p.getName() + " escolhe a Histeria: " + h);
    }

    // ---------- Dissociação, sangramento ----------

    public void syncDissociation(Encounter e, EncounterParticipant p, CharacterSheet s) {
        boolean should = VitalRules.dissociated(s.getMentalFatigue());
        boolean has = hasCondition(p, DISSOCIATED);
        if (should && !has) addCondition(e, p, DISSOCIATED, ConditionExpiry.MANUAL, null, null);
        else if (!should && has) removeCondition(e, p, DISSOCIATED);
    }

    /**
     * Ferimento permanente de combate (Rompimento: golpes desarmados são contundentes): crítico com dado de dano no
     * máximo. Gravidade pela proporção dano / HP máximo; HP máximo cai metade do dano; Limitador do atributo da região
     * cai conforme a gravidade. O Mestre pode trocar a região ou remover (ajustes da ficha).
     */
    public void permanentInjury(Encounter e, EncounterParticipant p, CharacterSheet s, int damage, String region, Ctx c) {
        int before = SheetRules.effectiveHpMax(s.getHpMax(), s.getInjuries());
        InjurySeverity sev = SheetRules.injurySeverity(damage, before);
        String where = region == null || region.isBlank() ? "não localizado" : region.trim();
        boolean limb = SheetRules.injuryLimb(where);
        PermanentInjury injury = PermanentInjury.builder()
                .sheet(s).type(InjuryType.BLUNT).region((where + (limb ? " (Membro Inútil)" : "")))
                .severity(sev)
                .hpMaxReduction(SheetRules.injuryHpReduction(sev, InjuryType.BLUNT, s.getHpMax(), damage))
                .limiterReduction(SheetRules.injuryLimiterReduction(sev))
                .affectedAttribute(SheetRules.injuryAttribute(where))
                .build();
        s.getInjuries().add(injury);
        int after = SheetRules.effectiveHpMax(s.getHpMax(), s.getInjuries());
        if (s.getCurrentHp() > after) s.setCurrentHp(after);
        record(s, c, "COMBAT_INJURY", "effectiveHpMax", before, after);
        timeline.save(CombatEvent.builder().encounterId(e.getId()).round(e.getRound()).turnCounter(e.getTurnCounter())
                .participantId(p.getId()).type(CombatEventType.GM)
                .text("Ferimento permanente em " + p.getName() + ": Rompimento — " + injury.getRegion() + ", "
                        + sev.name() + " (−" + injury.getHpMaxReduction() + " HP máx."
                        + (injury.getAffectedAttribute() == null ? "" : ", Limitador de " + injury.getAffectedAttribute()
                        + " −" + injury.getLimiterReduction()) + ")").build());
    }

    /** Abre Feridas (sangramento: −1 HP por Ferida no início de cada turno). */
    public void openWounds(Encounter e, EncounterParticipant p, CharacterSheet s, int n, Ctx c) {
        if (n <= 0) return;
        int before = s.getOpenWounds();
        s.setOpenWounds(before + n);
        record(s, c, "COMBAT_WOUND", "openWounds", before, before + n);
        log(e, p.getId(), CombatEventType.ACTION, p.getName() + ": +" + n + (n == 1 ? " Ferida aberta" : " Feridas abertas"));
    }

    public void stanch(Encounter e, EncounterParticipant executor, EncounterParticipant target, CharacterSheet ts, Ctx c) {
        int before = ts.getOpenWounds();
        ts.setOpenWounds(0);
        if (before != 0) record(ts, c, "COMBAT_STANCH", "openWounds", before, 0);
        log(e, executor.getId(), CombatEventType.ACTION, executor.getName() + " estancou o sangramento"
                + (executor.getId().equals(target.getId()) ? "" : " de " + target.getName()));
    }

    // ---------- ciclo do combate ----------

    /** Início do turno: Dissociação, sangramento, Beira da Morte, Exaustão, Adrenalina +2 e crise. */
    public void onTurnStart(Encounter e, EncounterParticipant p, CharacterSheet s, Ctx c) {
        syncDissociation(e, p, s);
        if (hasCondition(p, DEAD)) return;
        if (s.getOpenWounds() > 0) {
            log(e, p.getId(), CombatEventType.ACTION, p.getName() + " sangra: " + s.getOpenWounds() + " de dano");
            applyHp(e, p, s, -s.getOpenWounds(), new Ctx(c.userId(), c.role(), "Sangramento", false));
        }
        environmentStart(e, p, s, c);
        List<CombatCondition> mine = conds(p);
        if (has(mine, DEAD)) return;
        if (has(mine, DYING)) deathSave(e, p, s, c);
        if (p.getExhaustionNd() != null && has(mine, EXHAUSTED) && s.getCurrentSp() == 0) exhaustionTest(e, p, s, c);
        setAdrenaline(p, p.getAdrenaline() + 2);
        if (VitalRules.crisisByLowHp(s.getCurrentHp(), s.getHpMax())) crisis(e, p);
    }

    // ---------- condições de ambiente (§33) ----------

    /** Teste de atributo (1d20 + mod. − Desgaste) contra `nd`, com a vantagem dada; registra na linha do tempo. */
    public boolean attributeTest(Encounter e, EncounterParticipant p, CharacterSheet s, Attribute a, int nd,
                                 String label, int adv, Ctx c) {
        List<RollPart> hit = new ArrayList<>();
        String name = RollProfileService.label(a);
        hit.add(new RollPart("HIT", name, RollRules.modOrZero(s.getAttribute(a)), null, false));
        boolean mentalAttr = a == Attribute.INT || a == Attribute.CHA || a == Attribute.MEN;
        int pen = CombatRules.fatiguePenalty(mentalAttr ? s.getMentalFatigue() : s.getPhysicalFatigue());
        if (pen > 0) hit.add(new RollPart("HIT", mentalAttr ? "Desgaste Mental" : "Desgaste Físico", -pen, null, false));
        Calc calc = new Calc(RollSourceType.ATTRIBUTE_TEST, null, "Teste de " + name, null, null, 0,
                RollRules.CRIT_MARGIN, 0, hit, null, List.of());
        RollMessage m = rolls.rollCalc(s, e.getCampaignId(), calc, adv, label + ": " + p.getName() + " — ND " + nd,
                c.userId(), c.role());
        boolean ok = m.total() >= nd;
        log(e, p.getId(), CombatEventType.ACTION, p.getName() + ": " + label + " " + m.total() + " vs ND " + nd
                + " — " + (ok ? "sucesso" : "falha"));
        return ok;
    }

    /** Desvantagem do próprio corpo num teste de atributo: Envenenado (CON), Congelando (FOR/DES). */
    public int conditionAdvantage(EncounterParticipant p, CharacterSheet s, Attribute a) {
        int adv = 0;
        if (a == Attribute.DEX && s != null && SheetRules.overloaded(s)) adv--; // Sobrecarregado
        if (a == Attribute.CON && hasCondition(p, POISONED)) adv--;
        if ((a == Attribute.STR || a == Attribute.DEX) && hasCondition(p, FREEZING)) adv--;
        return adv;
    }

    private int rollDamage(CharacterSheet s, String dice, String purpose, Ctx c) {
        return rolls.rollInternal(s, dice, purpose, c.userId(), c.role()).getTotal();
    }

    /** Início do turno: fogo, frio, veneno e falta de ar. */
    private void environmentStart(Encounter e, EncounterParticipant p, CharacterSheet s, Ctx c) {
        for (CombatCondition k : conds(p)) {
            switch (k.getConditionKey()) {
                case BURNING -> {
                    EnvironmentRules.Level l = EnvironmentRules.level(EnvironmentRules.BURN, k.getIntensity());
                    int d = rollDamage(s, l.dice(), "Queimando (" + l.label() + "): " + p.getName(), c);
                    log(e, p.getId(), CombatEventType.ACTION, p.getName() + " queima: " + d + " de dano");
                    applyHp(e, p, s, -d, new Ctx(c.userId(), c.role(), "Queimando", false));
                }
                case FREEZING -> {
                    int tests = k.getProgress() == null ? 0 : k.getProgress();
                    k.setProgress(tests + 1);
                    conditions.save(k);
                    if (!attributeTest(e, p, s, Attribute.CON, EnvironmentRules.freezeNd(tests), "Congelando", 0, c)) {
                        int d = rollDamage(s, EnvironmentRules.FREEZE_DAMAGE, "Congelando: " + p.getName(), c);
                        int before = s.getPhysicalFatigue();
                        s.setPhysicalFatigue(before + 1);
                        record(s, c, "COMBAT_FREEZE", "physicalFatigue", before, before + 1);
                        log(e, p.getId(), CombatEventType.ACTION, p.getName() + " congela: " + d + " de dano e +1 Desgaste Físico");
                        applyHp(e, p, s, -d, new Ctx(c.userId(), c.role(), "Congelando", false));
                    }
                }
                case POISONED -> {
                    EnvironmentRules.Level l = EnvironmentRules.level(EnvironmentRules.POISON, k.getIntensity());
                    int d = rollDamage(s, l.dice(), "Veneno (" + l.label() + "): " + p.getName(), c);
                    log(e, p.getId(), CombatEventType.ACTION, p.getName() + " sofre o veneno: " + d + (l.sp() ? " de SP" : " de dano"));
                    if (l.sp()) applySp(e, p, s, -d, c);
                    else applyHp(e, p, s, -d, new Ctx(c.userId(), c.role(), "Veneno", false));
                }
                case SUFFOCATING -> {
                    int d = rollDamage(s, EnvironmentRules.SUFFOCATE_SP, "Sufocando: " + p.getName(), c);
                    log(e, p.getId(), CombatEventType.ACTION, p.getName() + " sufoca: −" + d + " SP");
                    applySp(e, p, s, -d, c);
                }
                default -> { }
            }
            if (hasCondition(p, DEAD)) return;
        }
    }

    /** Fim do turno: teste de CON contra o veneno; 2 sucessos eliminam. */
    public void onTurnEnd(Encounter e, EncounterParticipant p, CharacterSheet s, Ctx c) {
        for (CombatCondition k : conds(p)) {
            if (!POISONED.equals(k.getConditionKey()) || hasCondition(p, DEAD)) continue;
            EnvironmentRules.Level l = EnvironmentRules.level(EnvironmentRules.POISON, k.getIntensity());
            if (!attributeTest(e, p, s, Attribute.CON, l.nd(), "Resistir ao veneno", conditionAdvantage(p, s, Attribute.CON), c)) continue;
            int ok = (k.getProgress() == null ? 0 : k.getProgress()) + 1;
            if (ok >= EnvironmentRules.POISON_CURE) removeCondition(e, p, POISONED);
            else {
                k.setProgress(ok);
                conditions.save(k);
            }
        }
    }

    /**
     * Estabilizar (Ação Maior de um aliado): INT contra ND 12 (+3 por falha anterior), vantagem com kit médico ou
     * medicina. Sucesso: sai da Beira da Morte e fica Estabilizado.
     */
    public void stabilize(Encounter e, EncounterParticipant executor, CharacterSheet es, EncounterParticipant target,
                          boolean medical, Ctx c) {
        int nd = VitalRules.stabilizeNd(target.getStabilizeFailures());
        if (attributeTest(e, executor, es, Attribute.INT, nd, "Estabilizar " + target.getName(), medical ? 1 : 0, c)) {
            removeCondition(e, target, DYING);
            addCondition(e, target, STABLE, ConditionExpiry.MANUAL, null, null);
            target.setStabilizeFailures(0);
        } else {
            target.setStabilizeFailures(target.getStabilizeFailures() + 1);
        }
    }

    /** Sequela de Nocaute no início do combate: −1 PQ e a pendência some. */
    public void knockoutSequel(Encounter e, EncounterParticipant p, CharacterSheet s, Ctx c) {
        if (!s.isKnockoutSequel()) return;
        s.setKnockoutSequel(false);
        int before = s.getCurrentKp();
        if (before > 0) {
            s.setCurrentKp(before - 1);
            record(s, c, "KNOCKOUT_SEQUEL", "currentKp", before, before - 1);
        }
        log(e, p.getId(), CombatEventType.ACTION, p.getName() + ": Sequela de Nocaute (−1 PQ)");
    }

    /** Apagar chamas (Ação Maior): DES contra o ND da intensidade; sucesso encerra o Queimando. */
    public void extinguish(Encounter e, EncounterParticipant p, CharacterSheet s, Ctx c) {
        CombatCondition k = conds(p).stream().filter(x -> BURNING.equals(x.getConditionKey())).findFirst().orElse(null);
        if (k == null) return;
        EnvironmentRules.Level l = EnvironmentRules.level(EnvironmentRules.BURN, k.getIntensity());
        if (attributeTest(e, p, s, Attribute.DEX, l.nd(), "Apagar chamas", conditionAdvantage(p, s, Attribute.DEX), c)) {
            removeCondition(e, p, BURNING);
        }
    }

    public void onRoundEnd(Encounter e, List<EncounterParticipant> ps) {
        for (EncounterParticipant p : ps) {
            if (p.getAdrenaline() >= VitalRules.MAX_ADRENALINE) p.setRoundsAtApex(p.getRoundsAtApex() + 1);
            if (!p.isEngagedThisRound() && p.getAdrenaline() > 0) {
                setAdrenaline(p, p.getAdrenaline() - 2);
                log(e, p.getId(), CombatEventType.ACTION, p.getName() + " esfria: Adrenalina " + p.getAdrenaline());
            }
            p.setEngagedThisRound(false);
        }
    }

    public void onCombatEnd(Encounter e, EncounterParticipant p, CharacterSheet s, Ctx c) {
        p.setAdrenaline(0);
        p.setHysteria(null);
        p.setRoundsAtApex(0);
        p.setExhaustionNd(null);
        int wounds = s.getOpenWounds();
        if (wounds != 0) {
            s.setOpenWounds(0);
            record(s, c, "COMBAT_END", "openWounds", wounds, 0);
        }
        for (CombatCondition k : conds(p)) {
            if (EXHAUSTED.equals(k.getConditionKey()) || DISSOCIATED.equals(k.getConditionKey())
                    || k.getExpiresAt() != ConditionExpiry.MANUAL) {
                conditions.delete(k);
            }
        }
    }
}
