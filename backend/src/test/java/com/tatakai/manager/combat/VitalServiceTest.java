package com.tatakai.manager.combat;

import com.tatakai.manager.dto.response.RollMessage;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.InvalidCombatException;
import com.tatakai.manager.repository.CombatConditionRepository;
import com.tatakai.manager.repository.CombatEventRepository;
import com.tatakai.manager.repository.EncounterParticipantRepository;
import com.tatakai.manager.service.ConditionCatalog;
import com.tatakai.manager.service.RollProfileService.Calc;
import com.tatakai.manager.service.RollService;
import com.tatakai.manager.service.SheetActivityLogService;
import com.tatakai.manager.service.VitalService;
import com.tatakai.manager.service.VitalService.Ctx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("VitalService — F6c desmaios, Adrenalina e sangramento")
class VitalServiceTest {

    @Mock private EncounterParticipantRepository participants;
    @Mock private CombatConditionRepository conditions;
    @Mock private CombatEventRepository timeline;
    @Mock private SheetActivityLogService activityLog;
    @Mock private RollService rolls;

    private VitalService vital;
    private Encounter enc;
    private CharacterSheet s;
    private EncounterParticipant p, ally;
    private final List<CombatCondition> cList = new ArrayList<>();
    private final List<CombatEvent> events = new ArrayList<>();
    private final Deque<Integer> totals = new ArrayDeque<>();
    private final List<Integer> advs = new ArrayList<>();
    private final List<String> purposes = new ArrayList<>();
    private final List<Calc> calcs = new ArrayList<>();
    private int d4 = 2;
    private final Ctx ctx = Ctx.of(UUID.randomUUID(), ActorRole.MASTER, "teste");

    @BeforeEach
    void setUp() {
        enc = Encounter.builder().id(UUID.randomUUID()).campaignId(UUID.randomUUID()).name("Briga")
                .status(EncounterStatus.ACTIVE).round(1).turnCounter(3).build();
        s = CharacterSheet.builder().id(UUID.randomUUID()).name("Kuro").hpMax(40).currentHp(40).spMax(10).currentSp(10)
                .kpMax(6).currentKp(6).con(30).men(30).build();
        p = EncounterParticipant.builder().id(UUID.randomUUID()).sheetId(s.getId()).name("Kuro").build();
        ally = EncounterParticipant.builder().id(UUID.randomUUID()).sheetId(UUID.randomUUID()).name("Aiko").build();
        lenient().when(participants.findByEncounterIdOrderByOrderIndex(any())).thenReturn(List.of(p, ally));
        lenient().when(conditions.findByParticipantIdIn(any())).thenAnswer(i -> {
            Collection<UUID> ids = i.getArgument(0);
            return new ArrayList<>(cList.stream().filter(c -> ids.contains(c.getParticipantId())).toList());
        });
        lenient().when(conditions.save(any(CombatCondition.class))).thenAnswer(i -> {
            CombatCondition c = i.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            if (!cList.contains(c)) cList.add(c);
            return c;
        });
        lenient().doAnswer(i -> cList.remove(i.<CombatCondition>getArgument(0))).when(conditions).delete(any(CombatCondition.class));
        lenient().when(timeline.save(any(CombatEvent.class))).thenAnswer(i -> {
            events.add(i.getArgument(0));
            return i.getArgument(0);
        });
        lenient().when(rolls.rollCalc(any(), any(), any(), anyInt(), anyString(), any(), any())).thenAnswer(i -> {
            calcs.add(i.getArgument(2));
            advs.add(i.getArgument(3));
            purposes.add(i.getArgument(4));
            int total = totals.isEmpty() ? 0 : totals.poll();
            return new RollMessage(UUID.randomUUID(), enc.getCampaignId(), s.getId(), null, "x", "y", RollChannel.PUBLIC,
                    RollMode.QUICK, i.getArgument(4), RollSourceType.ATTRIBUTE_TEST, null, "s", "f", "f", 0, List.of(),
                    List.of(), 0, total, 10, false, false, null, false, null, false, Instant.now(), null);
        });
        lenient().when(rolls.rollInternal(any(), anyString(), anyString(), any(), any()))
                .thenAnswer(i -> Roll.builder().total(d4).build());
        vital = new VitalService(participants, conditions, timeline, activityLog, rolls, new ConditionCatalog(), org.mockito.Mockito.mock(com.tatakai.manager.service.RollProfileService.class));
    }

    private Optional<CombatCondition> cond(String key) {
        return cList.stream().filter(c -> c.getParticipantId().equals(p.getId()) && c.getConditionKey().equals(key)).findFirst();
    }

    // ---------- HP ----------

    @Test
    @DisplayName("HP 0 em EASY e REAL: INCONSCIENTE (HP) manual, sem Beira da Morte")
    void hpZeroEasyReal() {
        for (CombatClassification cl : List.of(CombatClassification.EASY, CombatClassification.REAL)) {
            cList.clear();
            s.setCurrentHp(40);
            p.setClassification(cl);
            vital.applyHp(enc, p, s, -50, ctx);
            assertThat(s.getCurrentHp()).isZero();
            assertThat(cond("INCONSCIENTE")).get().satisfies(c -> {
                assertThat(c.getNote()).isEqualTo("HP");
                assertThat(c.getExpiresAt()).isEqualTo(ConditionExpiry.MANUAL);
            });
            assertThat(cond("BEIRA_DA_MORTE")).isEmpty();
            assertThat(cond("MORTO")).isEmpty();
        }
    }

    @Test
    @DisplayName("HP 0 em LETHAL: inconsciente + Beira da Morte; excedente >= metade do HP máximo mata")
    void hpZeroLethal() {
        p.setClassification(CombatClassification.LETHAL);
        vital.applyHp(enc, p, s, -45, ctx); // excedente 5 < 20
        assertThat(cond("INCONSCIENTE")).isPresent();
        assertThat(cond("BEIRA_DA_MORTE")).isPresent();
        assertThat(cond("MORTO")).isEmpty();

        cList.clear();
        s.setCurrentHp(40);
        vital.applyHp(enc, p, s, -60, ctx); // excedente 20 >= 20
        assertThat(cond("MORTO")).isPresent();
        assertThat(cond("BEIRA_DA_MORTE")).isEmpty();
    }

    @Test
    @DisplayName("Beira da Morte: dano gera falha (crítico +2); 3 falhas matam")
    void damageWhileDying() {
        p.setClassification(CombatClassification.LETHAL);
        vital.applyHp(enc, p, s, -41, ctx);
        vital.applyHp(enc, p, s, -1, ctx);
        assertThat(p.getDeathFailures()).isEqualTo(1);
        vital.applyHp(enc, p, s, -1, ctx.withCritical(true));
        assertThat(p.getDeathFailures()).isEqualTo(3);
        assertThat(cond("MORTO")).isPresent();
        assertThat(cond("BEIRA_DA_MORTE")).isEmpty();
    }

    @Test
    @DisplayName("Salvaguarda de morte no início do turno: 3 sucessos estabilizam, 3 falhas matam (ND 12, CON)")
    void deathSaves() {
        p.setClassification(CombatClassification.LETHAL);
        vital.applyHp(enc, p, s, -41, ctx);
        totals.addAll(List.of(12, 5, 20));
        vital.onTurnStart(enc, p, s, ctx);
        vital.onTurnStart(enc, p, s, ctx);
        vital.onTurnStart(enc, p, s, ctx);
        assertThat(p.getDeathSuccesses()).isEqualTo(2);
        assertThat(p.getDeathFailures()).isEqualTo(1);
        assertThat(purposes.get(0)).contains("ND 12").contains("Kuro");
        totals.addAll(List.of(13));
        vital.onTurnStart(enc, p, s, ctx);
        assertThat(cond("BEIRA_DA_MORTE")).isEmpty();
        assertThat(cond("ESTABILIZADO")).isPresent();

        cList.clear();
        p.setDeathFailures(0);
        p.setDeathSuccesses(0);
        s.setCurrentHp(40);
        vital.applyHp(enc, p, s, -41, ctx);
        totals.addAll(List.of(1, 1, 1));
        for (int i = 0; i < 3; i++) vital.onTurnStart(enc, p, s, ctx);
        assertThat(cond("MORTO")).isPresent();
    }

    @Test
    @DisplayName("Curar de HP 0 remove inconsciência por HP, Beira e contadores")
    void healFromZero() {
        p.setClassification(CombatClassification.LETHAL);
        vital.applyHp(enc, p, s, -41, ctx);
        vital.applyHp(enc, p, s, 5, ctx);
        assertThat(cList).isEmpty();
        assertThat(p.getDeathFailures()).isZero();
    }

    // ---------- SP ----------

    @Test
    @DisplayName("SP 0 com Vontade ND 12 bem-sucedida: Exausto (ND 14); testes seguintes sobem +2")
    void spZeroExhausted() {
        totals.addAll(List.of(12, 14, 15));
        vital.applySp(enc, p, s, -10, ctx);
        assertThat(cond("EXAUSTO")).isPresent();
        assertThat(p.getExhaustionNd()).isEqualTo(14);
        assertThat(cond("INCONSCIENTE")).isEmpty();
        assertThat(purposes.get(0)).contains("ND 12");

        vital.onTurnStart(enc, p, s, ctx); // ND 14, passa
        assertThat(purposes.get(1)).contains("ND 14");
        assertThat(p.getExhaustionNd()).isEqualTo(16);
        vital.onTurnStart(enc, p, s, ctx); // ND 16 com 15, falha
        assertThat(purposes.get(2)).contains("ND 16");
        assertThat(cond("EXAUSTO")).isEmpty();
        assertThat(cond("INCONSCIENTE")).get().satisfies(c -> {
            assertThat(c.getNote()).isEqualTo("SP");
            assertThat(c.getExpiresAt()).isEqualTo(ConditionExpiry.COUNTER);
            assertThat(c.getTurnsRemaining()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("SP 0 com falha de Vontade: INCONSCIENTE (SP) COUNTER 3; ao acordar SP = 1")
    void spZeroFail() {
        totals.add(3);
        vital.applySp(enc, p, s, -10, ctx);
        CombatCondition c = cond("INCONSCIENTE").orElseThrow();
        assertThat(c.getNote()).isEqualTo("SP");
        assertThat(p.getExhaustionNd()).isNull();
        vital.onWake(enc, p, s, c, ctx);
        assertThat(s.getCurrentSp()).isEqualTo(1);
    }

    @Test
    @DisplayName("Recuperar SP remove Exausto")
    void recoverRemovesExhausted() {
        totals.add(15);
        vital.applySp(enc, p, s, -10, ctx);
        vital.applySp(enc, p, s, 3, ctx);
        assertThat(cond("EXAUSTO")).isEmpty();
        assertThat(p.getExhaustionNd()).isNull();
        assertThat(s.getCurrentSp()).isEqualTo(3);
    }

    @Test
    @DisplayName("Colapso (Desgaste Físico >= 12): SP não recupera")
    void collapseBlocksRecovery() {
        s.setCurrentSp(0);
        s.setPhysicalFatigue(12);
        vital.applySp(enc, p, s, 4, ctx);
        assertThat(s.getCurrentSp()).isZero();
    }

    @Test
    @DisplayName("Dissociado: zerar SP derruba sem teste de Vontade")
    void dissociatedSpZero() {
        s.setMentalFatigue(12);
        vital.applySp(enc, p, s, -10, ctx);
        verify(rolls, never()).rollCalc(any(), any(), any(), anyInt(), anyString(), any(), any());
        assertThat(cond("INCONSCIENTE")).get().extracting(CombatCondition::getNote).isEqualTo("SP");
    }

    // ---------- PQ ----------

    @Test
    @DisplayName("PQ 0: Vontade ND 14 + 2 por zeragem; sucesso deixa PQ 2")
    void kpZeroSuccess() {
        totals.addAll(List.of(14, 15));
        vital.applyKp(enc, p, s, -6, ctx);
        assertThat(s.getCurrentKp()).isEqualTo(2);
        assertThat(cond("INCONSCIENTE")).isEmpty();
        assertThat(purposes.get(0)).contains("ND 14");
        vital.applyKp(enc, p, s, -2, ctx);
        assertThat(purposes.get(1)).contains("ND 16");
        assertThat(p.getKpZeroings()).isEqualTo(2);
        assertThat(cond("INCONSCIENTE")).isPresent(); // 15 < 16
    }

    @Test
    @DisplayName("PQ 0 com falha: COUNTER 1d4+2; ao acordar PQ 1 e Desorientado até o fim do próximo turno")
    void kpZeroFailWake() {
        totals.add(5);
        d4 = 3;
        vital.applyKp(enc, p, s, -6, ctx);
        CombatCondition c = cond("INCONSCIENTE").orElseThrow();
        assertThat(c.getNote()).isEqualTo("PQ");
        assertThat(c.getTurnsRemaining()).isEqualTo(5);
        conditions.delete(c);
        vital.onWake(enc, p, s, c, ctx);
        assertThat(s.getCurrentKp()).isEqualTo(1);
        assertThat(cond("DESORIENTADO")).get().extracting(CombatCondition::getExpiresAt)
                .isEqualTo(ConditionExpiry.END_OF_TARGET_TURN);
    }

    @Test
    @DisplayName("Vontade: desgaste mental penaliza, Fluxo dá vantagem e Muralha soma +4")
    void willParts() {
        s.setMentalFatigue(3); // Cansado −1
        p.setAdrenaline(10);
        p.setHysteria(Hysteria.WALL);
        totals.add(20);
        vital.applySp(enc, p, s, -10, ctx);
        assertThat(advs.get(0)).isEqualTo(1);
        assertThat(calcs.get(0).hit()).extracting(RollPart::source).contains("MEN", "Desgaste Mental", "Muralha");
    }

    // ---------- Adrenalina ----------

    @Test
    @DisplayName("Início do turno: Adrenalina +2; chega a 5 e a Fluxo; HP <= 1/4 vira crise (10)")
    void adrenalineTurnStart() {
        vital.onTurnStart(enc, p, s, ctx);
        assertThat(p.getAdrenaline()).isEqualTo(2);
        p.setAdrenaline(4);
        vital.onTurnStart(enc, p, s, ctx);
        assertThat(p.getAdrenaline()).isEqualTo(6);
        s.setCurrentHp(10); // 1/4 de 40
        vital.onTurnStart(enc, p, s, ctx);
        assertThat(p.getAdrenaline()).isEqualTo(10);
    }

    @Test
    @DisplayName("Crise: metade do HP num turno ou ser reduzido a HP <= 1/4")
    void adrenalineCrisis() {
        vital.applyHp(enc, p, s, -19, ctx);
        assertThat(p.getAdrenaline()).isZero();
        vital.applyHp(enc, p, s, -1, ctx); // 20 no turno
        assertThat(p.getAdrenaline()).isEqualTo(10);

        p.setAdrenaline(0);
        p.setDamageThisTurn(0);
        s.setCurrentHp(40);
        vital.applyHp(enc, p, s, -30, ctx); // HP 10 <= 10 (e 30 >= 20)
        assertThat(p.getAdrenaline()).isEqualTo(10);
    }

    @Test
    @DisplayName("Salvar o Próximo: aliado com HP 0 dá +5 uma vez a cada outro do time")
    void saveTheNext() {
        ally.setTeam(1);
        vital.applyHp(enc, p, s, -41, ctx);
        assertThat(ally.getAdrenaline()).isEqualTo(5);
        assertThat(ally.isSaveNextUsed()).isTrue();
        s.setCurrentHp(40);
        vital.applyHp(enc, p, s, 1, ctx);
        vital.applyHp(enc, p, s, -41, ctx);
        assertThat(ally.getAdrenaline()).isEqualTo(5);
        assertThat(p.getAdrenaline()).isNotEqualTo(5);
    }

    @Test
    @DisplayName("Salvar o Próximo não vale para o time adversário")
    void saveTheNextOtherTeam() {
        ally.setTeam(2);
        vital.applyHp(enc, p, s, -41, ctx);
        assertThat(ally.getAdrenaline()).isZero();
    }

    @Test
    @DisplayName("Fim da rodada: -2 sem engajamento, flag zerada, Ápice conta rodadas; sair do Ápice limpa Histeria")
    void cooling() {
        p.setAdrenaline(6);
        ally.setAdrenaline(10);
        ally.setHysteria(Hysteria.FURY);
        ally.setEngagedThisRound(true);
        p.setEngagedThisRound(false);
        vital.onRoundEnd(enc, List.of(p, ally));
        assertThat(p.getAdrenaline()).isEqualTo(4);
        assertThat(ally.getAdrenaline()).isEqualTo(10);
        assertThat(ally.getRoundsAtApex()).isEqualTo(1);
        assertThat(ally.isEngagedThisRound()).isFalse();
        vital.onRoundEnd(enc, List.of(p, ally));
        assertThat(ally.getAdrenaline()).isEqualTo(8);
        assertThat(ally.getHysteria()).isNull();
        assertThat(ally.getRoundsAtApex()).isZero();
    }

    @Test
    @DisplayName("Histeria só pode ser escolhida no Ápice")
    void hysteriaRequiresApex() {
        p.setAdrenaline(9);
        assertThatThrownBy(() -> vital.setHysteria(enc, p, Hysteria.FURY)).isInstanceOf(InvalidCombatException.class);
        p.setAdrenaline(10);
        vital.setHysteria(enc, p, Hysteria.WALL);
        assertThat(p.getHysteria()).isEqualTo(Hysteria.WALL);
    }

    // ---------- sangramento / dissociação / fim ----------

    @Test
    @DisplayName("Sangramento: início do turno tira HP igual às Feridas abertas")
    void bleeding() {
        s.setOpenWounds(3);
        vital.onTurnStart(enc, p, s, ctx);
        assertThat(s.getCurrentHp()).isEqualTo(37);
    }

    @Test
    @DisplayName("Dissociação: condição aplicada com Desgaste Mental >= 12 e removida abaixo")
    void dissociationSync() {
        s.setMentalFatigue(12);
        vital.syncDissociation(enc, p, s);
        assertThat(cond("DISSOCIADO")).isPresent();
        s.setMentalFatigue(5);
        vital.syncDissociation(enc, p, s);
        assertThat(cond("DISSOCIADO")).isEmpty();
    }

    @Test
    @DisplayName("Fim do combate: Adrenalina 0, Feridas 0, remove Exausto/Dissociado/não MANUAL; INCONSCIENTE (HP) fica")
    void combatEnd() {
        p.setAdrenaline(10);
        p.setHysteria(Hysteria.FURY);
        s.setOpenWounds(4);
        vital.addCondition(enc, p, "EXAUSTO", ConditionExpiry.MANUAL, null, null);
        vital.addCondition(enc, p, "DISSOCIADO", ConditionExpiry.MANUAL, null, null);
        vital.addCondition(enc, p, "CEGO", ConditionExpiry.END_OF_ROUND, null, null);
        vital.addCondition(enc, p, "INCONSCIENTE", ConditionExpiry.MANUAL, null, "HP");
        vital.onCombatEnd(enc, p, s, ctx);
        assertThat(p.getAdrenaline()).isZero();
        assertThat(p.getHysteria()).isNull();
        assertThat(s.getOpenWounds()).isZero();
        assertThat(cList).extracting(CombatCondition::getConditionKey).containsExactly("INCONSCIENTE");
    }
}
