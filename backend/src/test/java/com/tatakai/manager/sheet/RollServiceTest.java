package com.tatakai.manager.sheet;

import com.tatakai.manager.dto.request.RollRequest;
import com.tatakai.manager.dto.response.RollMessage;
import com.tatakai.manager.dto.response.SheetUpdateMessage;
import com.tatakai.manager.entity.ActivityCategory;
import com.tatakai.manager.entity.ActorRole;
import com.tatakai.manager.entity.Campaign;
import com.tatakai.manager.entity.CampaignMember;
import com.tatakai.manager.entity.CharacterSheet;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.entity.Roll;
import com.tatakai.manager.entity.User;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.InvalidRollException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CharacterSheetRepository;
import com.tatakai.manager.repository.RollRepository;
import com.tatakai.manager.repository.UserRepository;
import com.tatakai.manager.service.RollService;
import com.tatakai.manager.service.SheetActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RollService — Sprint 9 — F1 Ficha (RN-REC-01)")
class RollServiceTest {

    @Mock private RollRepository rollRepository;
    @Mock private CharacterSheetRepository sheetRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private SheetActivityLogService activityLog;
    @Mock private ApplicationEventPublisher events;

    private RollService service;
    private Campaign campaign;
    private User owner;
    private User master;
    private User other;
    private CharacterSheet sheet;

    @BeforeEach
    void setUp() {
        service = new RollService(rollRepository, sheetRepository, memberRepository, userRepository,
                activityLog, events, null, null);
        campaign = Campaign.builder().id(UUID.randomUUID()).build();
        owner = User.builder().id(UUID.randomUUID()).name("Ana").build();
        master = User.builder().id(UUID.randomUUID()).name("Mestre").build();
        other = User.builder().id(UUID.randomUUID()).name("Bia").build();
        CampaignMember ownerMember = member(owner, Role.PLAYER);
        member(master, Role.MASTER);
        member(other, Role.PLAYER);
        sheet = CharacterSheet.builder().id(UUID.randomUUID()).campaignMember(ownerMember).build();
        lenient().when(sheetRepository.findById(sheet.getId())).thenReturn(Optional.of(sheet));
        lenient().when(rollRepository.save(any(Roll.class))).thenAnswer(i -> i.getArgument(0));
    }

    private CampaignMember member(User u, Role role) {
        CampaignMember m = CampaignMember.builder().id(UUID.randomUUID()).campaign(campaign).user(u).role(role).build();
        lenient().when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), u.getId()))
                .thenReturn(Optional.of(m));
        return m;
    }

    @Test
    @DisplayName("parse aceita NdM e NdM+K / NdM-K")
    void parse_valid() {
        assertThat(RollService.parse("2d6+3")).isEqualTo(new RollService.DiceExpression(2, 6, 3));
        assertThat(RollService.parse("1d20-2")).isEqualTo(new RollService.DiceExpression(1, 20, -2));
        assertThat(RollService.parse("3d8")).isEqualTo(new RollService.DiceExpression(3, 8, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "d20", "0d6", "21d6", "1d1", "1d0", "2d6++1", "abc", "1d20+1000", "2x6"})
    @DisplayName("parse rejeita expressões inválidas")
    void parse_invalid(String expr) {
        assertThatThrownBy(() -> RollService.parse(expr)).isInstanceOf(InvalidRollException.class);
    }

    @Test
    @DisplayName("parse rejeita null")
    void parse_null() {
        assertThatThrownBy(() -> RollService.parse(null)).isInstanceOf(InvalidRollException.class);
    }

    @Test
    @DisplayName("total fica dentro do intervalo possível em muitas rolagens")
    void roll_totalWithinRange() {
        for (int i = 0; i < 300; i++) {
            RollMessage r = service.roll(sheet.getId(), new RollRequest("2d6+3", "teste"), owner.getId());
            assertThat(r.diceResults()).hasSize(2).allMatch(d -> d >= 1 && d <= 6);
            assertThat(r.total()).isBetween(5, 15)
                    .isEqualTo(r.diceResults().stream().mapToInt(Integer::intValue).sum() + 3);
        }
    }

    @Test
    @DisplayName("naturalD20 só existe para 1d20 (com ou sem modificador)")
    void roll_naturalD20OnlyForSingleD20() {
        RollMessage d20 = service.roll(sheet.getId(), new RollRequest("1d20+2", "ataque"), owner.getId());
        assertThat(d20.naturalD20()).isNotNull().isBetween(1, 20);
        assertThat(d20.naturalD20()).isEqualTo(d20.diceResults().get(0));
        assertThat(d20.critical()).isEqualTo(d20.naturalD20() == 20);
        assertThat(d20.fumble()).isEqualTo(d20.naturalD20() == 1);

        assertThat(service.roll(sheet.getId(), new RollRequest("2d20", "x"), owner.getId()).naturalD20()).isNull();
        assertThat(service.roll(sheet.getId(), new RollRequest("1d12", "x"), owner.getId()).naturalD20()).isNull();
    }

    @Test
    @DisplayName("dono rola: salva Roll, registra no histórico e publica evento ROLL")
    void roll_owner_savesLogsAndPublishes() {
        service.roll(sheet.getId(), new RollRequest("1d20+1", "percepção"), owner.getId());

        ArgumentCaptor<Roll> rollCap = ArgumentCaptor.forClass(Roll.class);
        verify(rollRepository).save(rollCap.capture());
        Roll saved = rollCap.getValue();
        assertThat(saved.getCampaignId()).isEqualTo(campaign.getId());
        assertThat(saved.getSheetId()).isEqualTo(sheet.getId());
        assertThat(saved.getActorId()).isEqualTo(owner.getId());
        assertThat(saved.getModifier()).isEqualTo(1);
        assertThat(saved.getExpression()).isEqualTo("1d20+1");

        verify(activityLog).record(eq(sheet), eq(owner.getId()), eq(ActorRole.PLAYER), eq(ActivityCategory.ROLL),
                eq("ROLL"), any(), any(), any(), any(), eq("percepção"));
        ArgumentCaptor<SheetUpdateMessage> ev = ArgumentCaptor.forClass(SheetUpdateMessage.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().event()).isEqualTo(SheetUpdateMessage.SheetEvent.ROLL);
        assertThat(ev.getValue().sheetId()).isEqualTo(sheet.getId());
        assertThat(ev.getValue().actorName()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("Mestre pode rolar em qualquer ficha (ator MASTER)")
    void roll_master_ok() {
        service.roll(sheet.getId(), new RollRequest("1d6", "x"), master.getId());

        verify(activityLog).record(eq(sheet), eq(master.getId()), eq(ActorRole.MASTER),
                eq(ActivityCategory.ROLL), eq("ROLL"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("outro jogador não rola na ficha alheia (403)")
    void roll_other_denied() {
        assertThatThrownBy(() -> service.roll(sheet.getId(), new RollRequest("1d6", "x"), other.getId()))
                .isInstanceOf(AccessDeniedException.class);
        verify(rollRepository, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("não-membro não rola (403)")
    void roll_nonMember_denied() {
        assertThatThrownBy(() -> service.roll(sheet.getId(), new RollRequest("1d6", "x"), UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
