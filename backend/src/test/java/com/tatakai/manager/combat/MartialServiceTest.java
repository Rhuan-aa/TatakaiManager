package com.tatakai.manager.combat;

import com.tatakai.manager.entity.CharacterArt;
import com.tatakai.manager.entity.CharacterArtStatus;
import com.tatakai.manager.entity.CharacterSheet;
import com.tatakai.manager.entity.CombatCounter;
import com.tatakai.manager.entity.Encounter;
import com.tatakai.manager.entity.EncounterParticipant;
import com.tatakai.manager.exception.InvalidCombatException;
import com.tatakai.manager.repository.CharacterArtRepository;
import com.tatakai.manager.repository.CharacterTechniqueRepository;
import com.tatakai.manager.repository.CombatCounterRepository;
import com.tatakai.manager.repository.CombatEventRepository;
import com.tatakai.manager.repository.MartialArtRepository;
import com.tatakai.manager.repository.RivalRepository;
import com.tatakai.manager.service.ArtCounterCatalog;
import com.tatakai.manager.service.MartialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/** F6d: contadores de arte (eventos automáticos, reset, ajuste manual). */
@ExtendWith(MockitoExtension.class)
class MartialServiceTest {

    @Mock private CharacterArtRepository arts;
    @Mock private MartialArtRepository catalog;
    @Mock private CharacterTechniqueRepository techniques;
    @Mock private RivalRepository rivals;
    @Mock private CombatCounterRepository counters;
    @Mock private CombatEventRepository timeline;

    private MartialService service;
    private final List<CombatCounter> rows = new ArrayList<>();
    private Encounter enc;
    private CharacterSheet sheet;
    private EncounterParticipant p;
    private final List<CharacterArt> charArts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new MartialService(arts, catalog, techniques, rivals, counters, new ArtCounterCatalog(), timeline);
        enc = Encounter.builder().id(UUID.randomUUID()).round(1).turnCounter(1).build();
        sheet = CharacterSheet.builder().id(UUID.randomUUID()).name("Aiko").build();
        p = EncounterParticipant.builder().id(UUID.randomUUID()).name("Aiko").sheetId(sheet.getId()).build();
        lenient().when(arts.findBySheetIdOrderByCreatedAt(sheet.getId())).thenAnswer(i -> charArts);
        lenient().when(counters.findByParticipantIdIn(any())).thenAnswer(i -> new ArrayList<>(rows));
        lenient().when(counters.save(any(CombatCounter.class))).thenAnswer(i -> {
            CombatCounter c = i.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            if (!rows.contains(c)) rows.add(c);
            return c;
        });
    }

    private void learn(String art) {
        charArts.add(CharacterArt.builder().id(UUID.randomUUID()).sheet(sheet).artId(UUID.randomUUID()).artName(art)
                .status(CharacterArtStatus.ACTIVE).build());
    }

    private int value(String key) {
        return rows.stream().filter(c -> c.getCounterKey().equals(key)).findFirst().map(CombatCounter::getValue).orElse(-1);
    }

    private void fire(String event) {
        service.fire(enc, p, sheet, event, null, null);
    }

    @Test
    @DisplayName("Ritmo (Capoeira): +1 esquiva, +1 contra-ataque, +1 acerto; −2 ao ser atingido (piso 0); zera se Derrubado/Agarrado")
    void ritmo() {
        learn("Capoeira");
        fire("DODGE_SUCCESS");
        fire("COUNTER_SUCCESS");
        fire("HIT");
        assertThat(value("RITMO")).isEqualTo(3);
        fire("GOT_HIT");
        assertThat(value("RITMO")).isEqualTo(1);
        fire("GOT_HIT");
        assertThat(value("RITMO")).isZero();
        for (int i = 0; i < 9; i++) fire("DODGE_SUCCESS");
        assertThat(value("RITMO")).isEqualTo(5); // teto
        fire("KNOCKED_DOWN");
        assertThat(value("RITMO")).isZero();
        fire("DODGE_SUCCESS");
        fire("GRAPPLED");
        assertThat(value("RITMO")).isZero();
    }

    @Test
    @DisplayName("Embalo (Boxe): +1 esquiva e +1 golpe da própria arte; golpe de outra arte não conta; ser atingido zera")
    void embalo() {
        learn("Boxe");
        fire("DODGE_SUCCESS");
        service.fire(enc, p, sheet, "HIT", null, "Boxe");
        service.fire(enc, p, sheet, "HIT", null, "Karatê");
        service.fire(enc, p, sheet, "HIT", null, null);
        assertThat(value("EMBALO")).isEqualTo(2);
        fire("GOT_HIT");
        assertThat(value("EMBALO")).isZero();
    }

    @Test
    @DisplayName("Força Centrífuga (Taekwondo): +2 por técnica da arte, teto 10; técnica de outra arte não conta")
    void centrifuga() {
        learn("Taekwondo");
        service.fire(enc, p, sheet, "ART_TECHNIQUE_USED", null, "Judô");
        assertThat(value("FORCA_CENTRIFUGA")).isEqualTo(-1); // nem criado
        for (int i = 0; i < 7; i++) service.fire(enc, p, sheet, "ART_TECHNIQUE_USED", null, "Taekwondo");
        assertThat(value("FORCA_CENTRIFUGA")).isEqualTo(10);
    }

    @Test
    @DisplayName("Foco (Karatê): +1 em defesa bem-sucedida, teto 1; COMBAT_END zera")
    void foco() {
        learn("Karatê");
        fire("DEFEND_SUCCESS");
        fire("DEFEND_SUCCESS");
        assertThat(value("FOCO")).isEqualTo(1);
        fire("COMBAT_END");
        assertThat(value("FOCO")).isZero();
    }

    @Test
    @DisplayName("Sem a arte ACTIVE, o evento não cria nem altera contador")
    void withoutArt() {
        fire("DODGE_SUCCESS");
        assertThat(rows).isEmpty();
        charArts.add(CharacterArt.builder().id(UUID.randomUUID()).sheet(sheet).artId(UUID.randomUUID()).artName("Capoeira")
                .status(CharacterArtStatus.REQUESTED).build());
        fire("DODGE_SUCCESS");
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("Mudança automática vira evento na linha do tempo; sem mudança (no teto) não")
    void timelineEvents() {
        learn("Karatê");
        fire("DEFEND_SUCCESS");
        org.mockito.Mockito.verify(timeline, org.mockito.Mockito.times(1)).save(any(com.tatakai.manager.entity.CombatEvent.class));
        fire("DEFEND_SUCCESS");
        org.mockito.Mockito.verify(timeline, org.mockito.Mockito.times(1)).save(any(com.tatakai.manager.entity.CombatEvent.class));
    }

    @Test
    @DisplayName("ensureAll cria os contadores das artes ACTIVE; perTarget um por outro participante; idempotente")
    void ensureAll() {
        learn("Boxe");
        learn("Sumo");
        EncounterParticipant q = EncounterParticipant.builder().id(UUID.randomUUID()).name("Bruno").sheetId(UUID.randomUUID()).build();
        CharacterSheet qs = CharacterSheet.builder().id(q.getSheetId()).name("Bruno").build();
        service.ensureAll(List.of(p, q), Map.of(p.getId(), sheet, q.getId(), qs));
        assertThat(rows).extracting(CombatCounter::getCounterKey).containsExactlyInAnyOrder("EMBALO", "TERRENO_PERDIDO");
        assertThat(rows.stream().filter(c -> c.getCounterKey().equals("TERRENO_PERDIDO")).findFirst().get()
                .getTargetParticipantId()).isEqualTo(q.getId());
        service.ensureAll(List.of(p, q), Map.of(p.getId(), sheet, q.getId(), qs));
        assertThat(rows).hasSize(2);
    }

    @Test
    @DisplayName("Ajuste manual: dentro de [min, max] grava; fora -> 400")
    void manualSet() {
        learn("Capoeira");
        fire("HIT");
        CombatCounter c = rows.get(0);
        service.set(enc, p, c, 4);
        assertThat(c.getValue()).isEqualTo(4);
        assertThatThrownBy(() -> service.set(enc, p, c, 6)).isInstanceOf(InvalidCombatException.class);
        assertThat(c.getValue()).isEqualTo(4);
    }
}
