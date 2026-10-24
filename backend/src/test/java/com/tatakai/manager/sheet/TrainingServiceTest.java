package com.tatakai.manager.sheet;

import com.tatakai.manager.entity.*;
import com.tatakai.manager.repository.*;
import com.tatakai.manager.service.CharacterTechniqueService;
import com.tatakai.manager.service.RollService;
import com.tatakai.manager.service.SheetActivityLogService;
import com.tatakai.manager.service.TrainingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrainingService — F4a treino e Repouso ao avançar o dia")
class TrainingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private CharacterSheetRepository sheetRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private TrainingEntryRepository entryRepository;
    @Mock private CharacterTechniqueRepository techniqueRepository;
    @Mock private CharacterTechniqueService techniqueService;
    @Mock private RollService rollService;
    @Mock private SheetActivityLogService activityLog;
    @Mock private ApplicationEventPublisher events;

    private TrainingService service;
    private final List<TrainingEntry> entries = new ArrayList<>();
    private final List<Booking> bookings = new ArrayList<>();
    private TimeSkip ts;
    private CharacterSheet sheet;
    private User player;

    @BeforeEach
    void setUp() {
        service = new TrainingService(bookingRepository, sheetRepository, memberRepository, entryRepository,
                techniqueRepository, techniqueService, rollService, activityLog, events);
        Campaign campaign = Campaign.builder().id(UUID.randomUUID()).build();
        player = User.builder().id(UUID.randomUUID()).name("Ana").build();
        CampaignMember m = CampaignMember.builder().id(UUID.randomUUID()).campaign(campaign).user(player)
                .role(Role.PLAYER).build();
        sheet = CharacterSheet.builder().id(UUID.randomUUID()).campaignMember(m).buildStatus(BuildStatus.COMPLETED)
                .bodyType(BodyType.ECTOMORPH).str(5).dex(10).con(10).intl(10).cha(10).men(10).tal(10)
                .hpMax(20).spMax(10).kpMax(3).currentHp(20).currentSp(5).currentKp(1)
                .injuries(new ArrayList<>()).build();
        ts = TimeSkip.builder().id(UUID.randomUUID()).campaign(campaign).currentDay((short) 1)
                .venue(TrainingVenue.FIELD).build();

        lenient().when(sheetRepository.findByCampaignMemberCampaignId(campaign.getId())).thenReturn(List.of(sheet));
        lenient().when(bookingRepository.findByTimeSkipDay_TimeSkipId(ts.getId())).thenAnswer(i -> List.copyOf(bookings));
        lenient().when(entryRepository.save(any())).thenAnswer(i -> {
            TrainingEntry e = i.getArgument(0);
            if (!entries.contains(e)) entries.add(e);
            return e;
        });
        lenient().when(entryRepository.findBySheetIdAndTimeSkipId(any(), any())).thenAnswer(i -> List.copyOf(entries));
        lenient().when(entryRepository.findBySheetIdOrderByCreatedAt(any())).thenAnswer(i -> List.copyOf(entries));
        lenient().when(entryRepository.countBySheetIdAndTarget(any(), anyString())).thenAnswer(i ->
                entries.stream().filter(e -> e.getTarget().equals(i.getArgument(1))).count());
        lenient().when(rollService.rollInternal(eq(sheet), anyString(), anyString(), any(), any()))
                .thenAnswer(i -> Roll.builder().total(((String) i.getArgument(1)).startsWith("2d") ? 6 : 12).build());
    }

    private void book(short day, short slot, SoloActivityType type, String target, RestQuality q) {
        TimeSkipDay d = TimeSkipDay.builder().id(UUID.randomUUID()).timeSkip(ts).dayNumber(day).build();
        bookings.add(Booking.builder().id(UUID.randomUUID()).timeSkipDay(d).user(player).slotNumber(slot)
                .soloActivityType(type).trainingTarget(target).restQuality(q).idlePointCost((short) 1).build());
    }

    @Test
    @DisplayName("treino gera ganho pendente e Desgaste; Repouso do mesmo dia consolida e recupera")
    void trainThenRest() {
        book((short) 1, (short) 1, SoloActivityType.TREINO, "DEX", null);
        book((short) 1, (short) 2, SoloActivityType.REPOUSO, null, RestQuality.SIMPLE);

        service.processUpTo(ts, (short) 2);

        // DEX: base 1 + modQ(10)/2 = 0 + Ectomorfo 1 = 2
        assertThat(sheet.getDex()).isEqualTo(12);
        assertThat(entries.get(0).getStatus()).isEqualTo(TrainingEntryStatus.CONSOLIDATED);
        assertThat(sheet.getPhysicalFatigue()).isZero(); // +1 do treino, −1 do Repouso Simples
        assertThat(sheet.getCurrentSp()).isEqualTo(10);   // 5 + 12 + 1, limitado ao máximo
        assertThat(sheet.getCurrentKp()).isEqualTo(3);
        assertThat(ts.getProcessedDay()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("dia sem Repouso: +1 Desgaste Físico e o ganho fica pendente; não reprocessa dias já processados")
    void noRest() {
        book((short) 1, (short) 1, SoloActivityType.TREINO, "STR", null);

        service.processUpTo(ts, (short) 2);
        assertThat(sheet.getStr()).isEqualTo(5);
        assertThat(entries.get(0).getStatus()).isEqualTo(TrainingEntryStatus.PENDING);
        assertThat(sheet.getPhysicalFatigue()).isEqualTo(2);

        service.processUpTo(ts, (short) 2);
        assertThat(entries).hasSize(1);
    }

    @Test
    @DisplayName("consolidação respeita a Barreira de Inaptidão (6 → 7 bloqueado)")
    void barrier() {
        sheet.setStr(6);
        book((short) 1, (short) 1, SoloActivityType.TREINO, "STR", null);
        book((short) 1, (short) 2, SoloActivityType.REPOUSO, null, RestQuality.COMFORTABLE);

        service.processUpTo(ts, (short) 2);

        assertThat(sheet.getStr()).isEqualTo(6);
    }
}
