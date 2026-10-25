package com.tatakai.manager.service;

import com.tatakai.manager.dto.response.EncounterResponse.ArtRef;
import com.tatakai.manager.entity.CharacterArt;
import com.tatakai.manager.entity.CharacterArtStatus;
import com.tatakai.manager.entity.CharacterSheet;
import com.tatakai.manager.entity.CharacterTechnique;
import com.tatakai.manager.entity.CombatCounter;
import com.tatakai.manager.entity.CombatEvent;
import com.tatakai.manager.entity.CombatEventType;
import com.tatakai.manager.entity.Encounter;
import com.tatakai.manager.entity.EncounterParticipant;
import com.tatakai.manager.entity.MartialArt;
import com.tatakai.manager.entity.MartialType;
import com.tatakai.manager.entity.MasteryBonus;
import com.tatakai.manager.entity.MoveKind;
import com.tatakai.manager.entity.ReactionType;
import com.tatakai.manager.entity.Rival;
import com.tatakai.manager.entity.TechniqueState;
import com.tatakai.manager.exception.InvalidCombatException;
import com.tatakai.manager.repository.CharacterArtRepository;
import com.tatakai.manager.repository.CharacterTechniqueRepository;
import com.tatakai.manager.repository.CombatCounterRepository;
import com.tatakai.manager.repository.CombatEventRepository;
import com.tatakai.manager.repository.MartialArtRepository;
import com.tatakai.manager.repository.RivalRepository;
import com.tatakai.manager.service.rules.MartialRules;
import com.tatakai.manager.service.rules.MartialRules.Family;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Contexto marcial de um lutador em combate (F6d): arte principal, tipo, especializações, Leitura, base ativa e
 * contadores de arte. A matemática vive em {@link MartialRules}; aqui só se juntam ficha, catálogo e encontro.
 */
@Service
@Transactional
public class MartialService {

    /** Arte principal (null = sem arte) + tipos/especializações da união das artes ACTIVE. */
    public record Fighter(List<CharacterArt> active, CharacterArt main, MartialType type, Set<Family> attack,
                          Set<ReactionType> reaction, Map<UUID, MartialType> typeByArt) {
        public boolean hasArt() {
            return !active.isEmpty();
        }
    }

    private final CharacterArtRepository arts;
    private final MartialArtRepository catalog;
    private final CharacterTechniqueRepository techniques;
    private final RivalRepository rivals;
    private final CombatCounterRepository counters;
    private final ArtCounterCatalog counterCatalog;
    private final CombatEventRepository timeline;

    public MartialService(CharacterArtRepository arts, MartialArtRepository catalog,
                          CharacterTechniqueRepository techniques, RivalRepository rivals,
                          CombatCounterRepository counters, ArtCounterCatalog counterCatalog,
                          CombatEventRepository timeline) {
        this.arts = arts;
        this.catalog = catalog;
        this.techniques = techniques;
        this.rivals = rivals;
        this.counters = counters;
        this.counterCatalog = counterCatalog;
        this.timeline = timeline;
    }

    // ---------- lutador ----------

    private MartialArt catalogOf(CharacterArt a) {
        return catalog.findById(a.getArtId()).or(() -> a.getArtName() == null ? java.util.Optional.empty()
                : catalog.findFirstByName(a.getArtName())).orElse(null);
    }

    @Transactional(readOnly = true)
    public Fighter fighter(EncounterParticipant p, CharacterSheet s) {
        List<CharacterArt> active = arts.findBySheetIdOrderByCreatedAt(s.getId()).stream()
                .filter(a -> a.getStatus() == CharacterArtStatus.ACTIVE).toList();
        Set<String> atk = new HashSet<>();
        Set<String> rea = new HashSet<>();
        Map<UUID, MartialType> types = new java.util.HashMap<>();
        for (CharacterArt a : active) {
            MartialArt m = catalogOf(a);
            if (m == null) continue;
            types.put(a.getId(), MartialRules.type(m.getMartialType()));
            if (m.getAttackSpecializations() != null) atk.addAll(m.getAttackSpecializations());
            if (m.getReactionSpecializations() != null) rea.addAll(m.getReactionSpecializations());
        }
        CharacterArt main = active.stream().filter(a -> a.getId().equals(p.getMainArtId())).findFirst()
                .orElseGet(() -> MartialRules.defaultMainArt(active));
        return new Fighter(active, main, main == null ? null : types.get(main.getId()),
                MartialRules.attackSpecs(atk), MartialRules.reactionSpecs(rea), types);
    }

    /** Tipo efetivo do golpe: arte da técnica (emprestada = a que a hospeda); avulsa ou básico = arte principal. */
    public MartialType attackType(Fighter f, CharacterTechnique t) {
        if (t != null && t.getCharacterArt() != null && f.typeByArt().containsKey(t.getCharacterArt().getId())) {
            return f.typeByArt().get(t.getCharacterArt().getId());
        }
        return f.type();
    }

    public boolean hasJoker(CharacterTechnique t) {
        return t != null && t.getBonuses() != null && t.getBonuses().contains(MasteryBonus.JOKER);
    }

    // ---------- base ativa ----------

    private CharacterTechnique activeBase(CharacterSheet s, UUID baseId) {
        if (baseId == null) return null;
        return techniques.findById(baseId).filter(x -> x.getSheet().getId().equals(s.getId())
                && x.getKind() == MoveKind.BASE && x.getState() == TechniqueState.ACTIVE).orElse(null);
    }

    /** Id da base ativa se ainda for ACTIVE na ficha; senão null (o ataque simplesmente não a usa). */
    @Transactional(readOnly = true)
    public UUID validBase(CharacterSheet s, UUID baseId) {
        return activeBase(s, baseId) == null ? null : baseId;
    }

    @Transactional(readOnly = true)
    public CharacterTechnique baseOrNull(CharacterSheet s, UUID baseId) {
        return activeBase(s, baseId);
    }

    /** CHANGE_BASE: a base precisa existir na ficha como BASE ACTIVE. */
    @Transactional(readOnly = true)
    public CharacterTechnique requireBase(CharacterSheet s, UUID baseId) {
        CharacterTechnique b = activeBase(s, baseId);
        if (b == null) throw new InvalidCombatException("Base não encontrada ou inativa");
        return b;
    }

    // ---------- Leitura de Oponente ----------

    /** Leitura (0–3) que `reader` tem contra `other`: Rival com o npcId dele ou o mesmo nome. */
    @Transactional(readOnly = true)
    public int read(CharacterSheet reader, EncounterParticipant other, CharacterSheet otherSheet) {
        return rivals.findBySheetIdOrderByName(reader.getId()).stream()
                .filter(r -> otherSheet != null && otherSheet.getNpcId() != null && Objects.equals(r.getNpcId(), otherSheet.getNpcId())
                        || r.getName() != null && r.getName().equalsIgnoreCase(other.getName()))
                .mapToInt(Rival::getReadBonus).max().orElse(0);
    }

    // ---------- ficha de apresentação ----------

    public record Info(UUID mainArtId, String mainArtName, MartialType type, UUID activeBaseId, String activeBaseName,
                       List<ArtRef> arts, List<ArtRef> bases) { }

    @Transactional(readOnly = true)
    public Info info(EncounterParticipant p, CharacterSheet s, boolean detail) {
        Fighter f = fighter(p, s);
        CharacterTechnique base = activeBase(s, p.getActiveBaseId());
        List<ArtRef> artRefs = null;
        List<ArtRef> baseRefs = null;
        if (detail) {
            artRefs = f.active().stream().map(a -> new ArtRef(a.getId(), a.getArtName())).toList();
            baseRefs = techniques.findBySheetIdOrderByCreatedAt(s.getId()).stream()
                    .filter(t -> t.getKind() == MoveKind.BASE && t.getState() == TechniqueState.ACTIVE)
                    .map(t -> new ArtRef(t.getId(), t.getName())).toList();
        }
        return new Info(f.main() == null ? null : f.main().getId(), f.main() == null ? null : f.main().getArtName(),
                f.type(), base == null ? null : base.getId(), base == null ? null : base.getName(), artRefs, baseRefs);
    }

    /** Valida e devolve a CharacterArt ACTIVE da ficha. */
    @Transactional(readOnly = true)
    public CharacterArt requireActiveArt(CharacterSheet s, UUID characterArtId) {
        return arts.findBySheetIdOrderByCreatedAt(s.getId()).stream()
                .filter(a -> a.getId().equals(characterArtId) && a.getStatus() == CharacterArtStatus.ACTIVE)
                .findFirst().orElseThrow(() -> new InvalidCombatException("Arte não encontrada ou inativa"));
    }

    // ---------- contadores de arte ----------

    @Transactional(readOnly = true)
    public List<CombatCounter> countersOf(Collection<UUID> participantIds) {
        return participantIds.isEmpty() ? List.of() : counters.findByParticipantIdIn(participantIds);
    }

    private static boolean hasArt(Fighter f, String artName) {
        return f.active().stream().anyMatch(a -> artName.equalsIgnoreCase(a.getArtName()));
    }

    /** Cria (idempotente) os contadores das artes ACTIVE de cada participante; perTarget: um por outro participante. */
    public void ensureAll(List<EncounterParticipant> ps, Map<UUID, CharacterSheet> sheetOf) {
        if (ps.isEmpty()) return;
        List<CombatCounter> have = new ArrayList<>(counters.findByParticipantIdIn(ps.stream().map(EncounterParticipant::getId).toList()));
        for (EncounterParticipant p : ps) {
            CharacterSheet s = sheetOf.get(p.getId());
            if (s == null) continue;
            Fighter f = fighter(p, s);
            for (ArtCounterCatalog.Def d : counterCatalog.all()) {
                if (!hasArt(f, d.art())) continue;
                if (!d.perTarget()) {
                    ensureRow(have, p.getId(), d.key(), null);
                } else {
                    for (EncounterParticipant q : ps) if (!q.getId().equals(p.getId())) ensureRow(have, p.getId(), d.key(), q.getId());
                }
            }
        }
    }

    private CombatCounter ensureRow(List<CombatCounter> have, UUID participantId, String key, UUID target) {
        for (CombatCounter c : have) {
            if (c.getParticipantId().equals(participantId) && c.getCounterKey().equals(key)
                    && Objects.equals(c.getTargetParticipantId(), target)) return c;
        }
        CombatCounter c = counters.save(CombatCounter.builder().participantId(participantId).counterKey(key)
                .targetParticipantId(target).value(0).build());
        have.add(c);
        return c;
    }

    /**
     * Dispara um evento do motor para os contadores do participante. `moveArt` = arte do golpe (para `sameArt`).
     * Cada mudança vira evento na linha do tempo.
     */
    public void fire(Encounter e, EncounterParticipant p, CharacterSheet s, String event, UUID target, String moveArt) {
        Fighter f = null;
        List<CombatCounter> mine = null;
        for (ArtCounterCatalog.Def d : counterCatalog.all()) {
            Integer delta = null;
            Integer reset = null;
            for (ArtCounterCatalog.Increment i : d.increments()) {
                if (i.event().equals(event) && (!i.sameArt() || (moveArt != null && moveArt.equalsIgnoreCase(d.art())))) {
                    delta = (delta == null ? 0 : delta) + i.delta();
                }
            }
            for (ArtCounterCatalog.Reset r : d.resets()) if (r.event().equals(event)) reset = r.value();
            if (delta == null && reset == null) continue;
            if (f == null) {
                f = fighter(p, s);
                mine = new ArrayList<>(counters.findByParticipantIdIn(List.of(p.getId())));
            }
            if (!hasArt(f, d.art())) continue;
            List<CombatCounter> rows = new ArrayList<>(mine.stream().filter(c -> c.getCounterKey().equals(d.key())
                    && (!d.perTarget() || target == null || target.equals(c.getTargetParticipantId()))).toList());
            if (rows.isEmpty() && (!d.perTarget() || target != null)) {
                rows.add(ensureRow(mine, p.getId(), d.key(), d.perTarget() ? target : null));
            }
            for (CombatCounter c : rows) {
                int before = c.getValue();
                int after = reset != null ? MartialRules.clamp(reset, d.min(), d.max())
                        : MartialRules.clamp(before + delta, d.min(), d.max());
                if (after == before) continue;
                c.setValue(after);
                counters.save(c);
                note(e, p, d.name() + " " + before + " → " + after + " (" + p.getName() + ")");
            }
        }
    }

    /** Ajuste manual (gastar/ajustar). */
    public void set(Encounter e, EncounterParticipant p, CombatCounter c, int value) {
        ArtCounterCatalog.Def d = counterCatalog.find(c.getCounterKey())
                .orElseThrow(() -> new InvalidCombatException("Contador desconhecido"));
        if (value < d.min() || value > d.max()) {
            throw new InvalidCombatException(d.name() + " vai de " + d.min() + " a " + d.max());
        }
        int before = c.getValue();
        c.setValue(value);
        counters.save(c);
        note(e, p, d.name() + " " + before + " → " + value + " (" + p.getName() + ")");
    }

    public String counterName(String key) {
        return counterCatalog.find(key).map(ArtCounterCatalog.Def::name).orElse(key);
    }

    public ArtCounterCatalog.Def counterDef(String key) {
        return counterCatalog.find(key).orElse(null);
    }

    private void note(Encounter e, EncounterParticipant p, String text) {
        timeline.save(CombatEvent.builder().encounterId(e.getId()).round(e.getRound()).turnCounter(e.getTurnCounter())
                .participantId(p.getId()).type(CombatEventType.ACTION).text(text).build());
    }
}
