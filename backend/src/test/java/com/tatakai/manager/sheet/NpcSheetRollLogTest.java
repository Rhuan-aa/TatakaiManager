package com.tatakai.manager.sheet;

import com.tatakai.manager.dto.request.RollRequest;
import com.tatakai.manager.dto.response.RollResponse;
import com.tatakai.manager.dto.response.SheetActivityLogResponse;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.repository.*;
import com.tatakai.manager.service.RollService;
import com.tatakai.manager.service.SheetActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Rolagem e histórico de ficha de NPC — Sprint 9 — F1.1 Ficha de NPC")
class NpcSheetRollLogTest {

    @Mock private RollRepository rollRepository;
    @Mock private CharacterSheetRepository sheetRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private SheetActivityLogService rollActivityLog;
    @Mock private ApplicationEventPublisher events;
    @Mock private SheetActivityLogRepository logRepository;

    private User owner;
    private User stranger;
    private CharacterSheet sheet;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(UUID.randomUUID()).name("Mestre").build();
        stranger = User.builder().id(UUID.randomUUID()).name("Intruso").build();
        sheet = CharacterSheet.builder().id(UUID.randomUUID()).npcId(UUID.randomUUID())
                .npcOwnerId(owner.getId()).build();
        lenient().when(sheetRepository.findById(sheet.getId())).thenReturn(Optional.of(sheet));
        lenient().when(rollRepository.save(any(Roll.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
    }

    @Test
    @DisplayName("RollService: dono rola na ficha de NPC com campaignId null e sem publicar evento")
    void roll_npcOwner_noEvent() {
        RollService rolls = new RollService(rollRepository, sheetRepository, memberRepository, userRepository,
                rollActivityLog, events);

        RollResponse r = rolls.roll(sheet.getId(), new RollRequest("1d20", "teste"), owner.getId());

        assertThat(r.actorName()).isEqualTo("Mestre");
        ArgumentCaptor<Roll> roll = ArgumentCaptor.forClass(Roll.class);
        verify(rollRepository).save(roll.capture());
        assertThat(roll.getValue().getCampaignId()).isNull();
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("RollService: não-dono em ficha de NPC -> CharacterSheetNotFoundException")
    void roll_npcNonOwner() {
        RollService rolls = new RollService(rollRepository, sheetRepository, memberRepository, userRepository,
                rollActivityLog, events);
        assertThatThrownBy(() -> rolls.roll(sheet.getId(), new RollRequest("1d20", "x"), stranger.getId()))
                .isInstanceOf(CharacterSheetNotFoundException.class);
        verify(rollRepository, never()).save(any());
    }

    @Test
    @DisplayName("SheetActivityLogService.record: ficha de NPC grava campaignId null")
    void record_npc_nullCampaign() {
        SheetActivityLogService logs = new SheetActivityLogService(logRepository, sheetRepository,
                memberRepository, userRepository);

        logs.record(sheet, owner.getId(), ActorRole.MASTER, ActivityCategory.BUILD, "CREATE_SHEET",
                null, null, "x", null, null);

        ArgumentCaptor<SheetActivityLog> cap = ArgumentCaptor.forClass(SheetActivityLog.class);
        verify(logRepository).save(cap.capture());
        assertThat(cap.getValue().getCampaignId()).isNull();
        assertThat(cap.getValue().getSheetId()).isEqualTo(sheet.getId());
    }

    @Test
    @DisplayName("SheetActivityLogService.listForSheet: ficha de NPC só para o dono")
    void listForSheet_npcOwnerOnly() {
        SheetActivityLogService logs = new SheetActivityLogService(logRepository, sheetRepository,
                memberRepository, userRepository);
        when(logRepository.findBySheetIdOrderByCreatedAtDesc(sheet.getId())).thenReturn(List.of());

        List<SheetActivityLogResponse> r = logs.listForSheet(sheet.getId(), owner.getId());

        assertThat(r).isEmpty();
        assertThatThrownBy(() -> logs.listForSheet(sheet.getId(), stranger.getId()))
                .isInstanceOf(CharacterSheetNotFoundException.class);
    }
}
