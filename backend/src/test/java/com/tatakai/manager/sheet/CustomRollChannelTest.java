package com.tatakai.manager.sheet;

import com.tatakai.manager.dto.request.CampaignRollRequest;
import com.tatakai.manager.dto.request.RollChannelRequest;
import com.tatakai.manager.dto.request.RollRequest;
import com.tatakai.manager.dto.response.RollChannelResponse;
import com.tatakai.manager.dto.response.RollMessage;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.InvalidRollException;
import com.tatakai.manager.exception.RollChannelNameTakenException;
import com.tatakai.manager.repository.*;
import com.tatakai.manager.service.RollChannelService;
import com.tatakai.manager.service.RollService;
import com.tatakai.manager.service.SheetActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Canais de rolagem personalizados (F5 — 20.6)")
class CustomRollChannelTest {

    @Mock private RollRepository rollRepository;
    @Mock private CharacterSheetRepository sheetRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private SheetActivityLogService activityLog;
    @Mock private ApplicationEventPublisher events;
    @Mock private CustomRollChannelRepository channelRepository;

    private RollService rolls;
    private RollChannelService channels;
    private Campaign campaign;
    private User master;
    private User ana;
    private User bia;
    private CustomRollChannel channel;

    @BeforeEach
    void setUp() {
        rolls = new RollService(rollRepository, sheetRepository, memberRepository, userRepository, activityLog,
                events, null, channelRepository);
        channels = new RollChannelService(channelRepository, memberRepository, rollRepository);
        campaign = Campaign.builder().id(UUID.randomUUID()).build();
        master = member("Mestre", Role.MASTER);
        ana = member("Ana", Role.PLAYER);
        bia = member("Bia", Role.PLAYER);
        channel = CustomRollChannel.builder().id(UUID.randomUUID()).campaignId(campaign.getId())
                .name("Cochicho").memberIds(List.of(ana.getId())).build();
        lenient().when(channelRepository.findById(channel.getId())).thenReturn(Optional.of(channel));
        lenient().when(channelRepository.findByCampaignIdOrderByCreatedAtAsc(campaign.getId()))
                .thenReturn(List.of(channel));
        lenient().when(channelRepository.save(any(CustomRollChannel.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(rollRepository.save(any(Roll.class))).thenAnswer(i -> i.getArgument(0));
    }

    private User member(String name, Role role) {
        User u = User.builder().id(UUID.randomUUID()).name(name).build();
        CampaignMember m = CampaignMember.builder().id(UUID.randomUUID()).campaign(campaign).user(u).role(role).build();
        lenient().when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), u.getId())).thenReturn(Optional.of(m));
        lenient().when(memberRepository.existsByCampaignIdAndUserId(campaign.getId(), u.getId())).thenReturn(true);
        lenient().when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    private RollChannelRequest req(String name, UUID... ids) {
        return new RollChannelRequest(name, List.of(ids));
    }

    @Test
    @DisplayName("lista: jogador vê só os canais em que está; Mestre vê todos")
    void listVisibility() {
        assertThat(channels.list(campaign.getId(), ana.getId())).extracting(RollChannelResponse::name)
                .containsExactly("Cochicho");
        assertThat(channels.list(campaign.getId(), bia.getId())).isEmpty();
        assertThat(channels.list(campaign.getId(), master.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Mestre cria canal com membros válidos")
    void create() {
        RollChannelResponse r = channels.create(campaign.getId(), req(" Segredo ", ana.getId(), bia.getId()), master.getId());
        assertThat(r.name()).isEqualTo("Segredo");
        assertThat(r.memberIds()).containsExactly(ana.getId(), bia.getId());
        assertThat(r.campaignId()).isEqualTo(campaign.getId());
    }

    @Test
    @DisplayName("jogador não cria canal (403)")
    void createByPlayer() {
        assertThatThrownBy(() -> channels.create(campaign.getId(), req("X", ana.getId()), ana.getId()))
                .isInstanceOf(AccessDeniedException.class);
        verify(channelRepository, never()).save(any());
    }

    @Test
    @DisplayName("nome duplicado na campanha -> 409")
    void duplicateName() {
        lenient().when(channelRepository.findByCampaignIdAndNameIgnoreCase(campaign.getId(), "cochicho"))
                .thenReturn(Optional.of(channel));
        assertThatThrownBy(() -> channels.create(campaign.getId(), req("cochicho"), master.getId()))
                .isInstanceOf(RollChannelNameTakenException.class);
    }

    @Test
    @DisplayName("participante fora da campanha -> 400")
    void outsiderMember() {
        assertThatThrownBy(() -> channels.create(campaign.getId(), req("X", UUID.randomUUID()), master.getId()))
                .isInstanceOf(InvalidRollException.class);
    }

    @Test
    @DisplayName("rolar em canal do qual não participa -> 403; participante e Mestre rolam")
    void rollAccess() {
        CampaignRollRequest gm = new CampaignRollRequest("1d20", "x", RollChannel.CUSTOM, channel.getId());
        RollMessage m = rolls.campaignRoll(campaign.getId(), gm, master.getId());
        assertThat(m.channel()).isEqualTo(RollChannel.CUSTOM);
        assertThat(m.customChannelId()).isEqualTo(channel.getId());
        verify(events).publishEvent(m);

        CampaignMember bm = memberRepository.findByCampaignIdAndUserId(campaign.getId(), bia.getId()).get();
        CharacterSheet sheet = CharacterSheet.builder().id(UUID.randomUUID()).campaignMember(bm).build();
        lenient().when(sheetRepository.findById(sheet.getId())).thenReturn(Optional.of(sheet));
        assertThatThrownBy(() -> rolls.roll(sheet.getId(),
                new RollRequest("1d20", "x", RollChannel.CUSTOM, campaign.getId(), channel.getId()), bia.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("histórico CUSTOM: não-membro 403; membro e Mestre veem")
    void history() {
        Roll r = Roll.builder().id(UUID.randomUUID()).campaignId(campaign.getId()).actorId(master.getId())
                .expression("1d20").diceResults("[5]").total(5).channel(RollChannel.CUSTOM)
                .customChannelId(channel.getId()).build();
        lenient().when(rollRepository.findCustom(any(UUID.class), any(Pageable.class))).thenReturn(List.of(r));

        assertThat(rolls.history(campaign.getId(), RollChannel.CUSTOM, channel.getId(), ana.getId())).hasSize(1);
        assertThat(rolls.history(campaign.getId(), RollChannel.CUSTOM, channel.getId(), master.getId())).hasSize(1);
        assertThatThrownBy(() -> rolls.history(campaign.getId(), RollChannel.CUSTOM, channel.getId(), bia.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> rolls.history(campaign.getId(), RollChannel.CUSTOM, UUID.randomUUID(), master.getId()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Mestre revela rolagem CUSTOM no público")
    void reveal() {
        Roll r = Roll.builder().id(UUID.randomUUID()).campaignId(campaign.getId()).actorId(master.getId())
                .expression("1d20").diceResults("[5]").total(5).channel(RollChannel.CUSTOM)
                .customChannelId(channel.getId()).revealed(false).build();
        lenient().when(rollRepository.findById(r.getId())).thenReturn(Optional.of(r));

        RollMessage m = rolls.reveal(campaign.getId(), r.getId(), master.getId());
        assertThat(m.revealed()).isTrue();
        verify(events).publishEvent(m);
        assertThatThrownBy(() -> rolls.reveal(campaign.getId(), r.getId(), ana.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("apagar canal apaga as rolagens dele; jogador não apaga")
    void deleteCascades() {
        assertThatThrownBy(() -> channels.delete(campaign.getId(), channel.getId(), ana.getId()))
                .isInstanceOf(AccessDeniedException.class);
        channels.delete(campaign.getId(), channel.getId(), master.getId());
        verify(rollRepository).deleteByCustomChannelId(channel.getId());
        verify(channelRepository).delete(channel);
    }
}
