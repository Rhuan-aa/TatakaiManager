package com.tatakai.manager.sheet;

import com.tatakai.manager.dto.response.SheetActivityLogResponse;
import com.tatakai.manager.entity.ActivityCategory;
import com.tatakai.manager.entity.ActorRole;
import com.tatakai.manager.entity.Campaign;
import com.tatakai.manager.entity.CampaignMember;
import com.tatakai.manager.entity.CharacterSheet;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.entity.SheetActivityLog;
import com.tatakai.manager.entity.User;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CharacterSheetRepository;
import com.tatakai.manager.repository.SheetActivityLogRepository;
import com.tatakai.manager.repository.UserRepository;
import com.tatakai.manager.service.SheetActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SheetActivityLogService — Sprint 9 — F1 Ficha (RN-COND-12)")
class SheetActivityLogServiceTest {

    @Mock private SheetActivityLogRepository logRepository;
    @Mock private CharacterSheetRepository sheetRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private UserRepository userRepository;

    private SheetActivityLogService service;
    private Campaign campaign;
    private User owner;
    private User master;
    private User other;
    private CharacterSheet sheet;

    @BeforeEach
    void setUp() {
        service = new SheetActivityLogService(logRepository, sheetRepository, memberRepository, userRepository);
        campaign = Campaign.builder().id(UUID.randomUUID()).build();
        owner = User.builder().id(UUID.randomUUID()).name("Ana").build();
        master = User.builder().id(UUID.randomUUID()).name("Mestre").build();
        other = User.builder().id(UUID.randomUUID()).name("Bia").build();
        CampaignMember ownerMember = member(owner, Role.PLAYER);
        member(master, Role.MASTER);
        member(other, Role.PLAYER);
        sheet = CharacterSheet.builder().id(UUID.randomUUID()).campaignMember(ownerMember).build();
        lenient().when(sheetRepository.findById(sheet.getId())).thenReturn(Optional.of(sheet));
    }

    private CampaignMember member(User u, Role role) {
        CampaignMember m = CampaignMember.builder().id(UUID.randomUUID()).campaign(campaign).user(u).role(role).build();
        lenient().when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), u.getId()))
                .thenReturn(Optional.of(m));
        return m;
    }

    private SheetActivityLog entry(UUID sheetId, ActivityCategory cat, String createdAt) {
        return SheetActivityLog.builder().id(UUID.randomUUID()).sheetId(sheetId).campaignId(campaign.getId())
                .actorId(master.getId()).actorRole(ActorRole.MASTER).category(cat).action("A")
                .createdAt(Instant.parse(createdAt)).build();
    }

    @Test
    @DisplayName("record grava entrada com campanha da ficha, valores como texto")
    void record_persists() {
        service.record(sheet, owner.getId(), ActorRole.PLAYER, ActivityCategory.RESOURCE, "UPDATE_RESOURCE",
                "HP", 10, 8, null, "dano");

        ArgumentCaptor<SheetActivityLog> cap = ArgumentCaptor.forClass(SheetActivityLog.class);
        verify(logRepository).save(cap.capture());
        SheetActivityLog l = cap.getValue();
        assertThat(l.getSheetId()).isEqualTo(sheet.getId());
        assertThat(l.getCampaignId()).isEqualTo(campaign.getId());
        assertThat(l.getBeforeValue()).isEqualTo("10");
        assertThat(l.getAfterValue()).isEqualTo("8");
        assertThat(l.getReason()).isEqualTo("dano");
    }

    @Test
    @DisplayName("dono e Mestre veem o histórico da ficha; outro jogador não (403)")
    void listForSheet_access() {
        when(logRepository.findBySheetIdOrderByCreatedAtDesc(sheet.getId()))
                .thenReturn(List.of(entry(sheet.getId(), ActivityCategory.ROLL, "2026-01-01T00:00:00Z")));
        when(userRepository.findAllById(any())).thenReturn(List.of(master));

        List<SheetActivityLogResponse> asOwner = service.listForSheet(sheet.getId(), owner.getId());
        List<SheetActivityLogResponse> asMaster = service.listForSheet(sheet.getId(), master.getId());

        assertThat(asOwner).hasSize(1);
        assertThat(asOwner.get(0).actorName()).isEqualTo("Mestre");
        assertThat(asMaster).hasSize(1);
        assertThatThrownBy(() -> service.listForSheet(sheet.getId(), other.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("histórico da campanha é restrito ao Mestre")
    void listForCampaign_masterOnly() {
        assertThatThrownBy(() -> service.listForCampaign(campaign.getId(), null, null, null, null, owner.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.listForCampaign(campaign.getId(), null, null, null, null, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("histórico da campanha filtra por ficha, categoria e período")
    void listForCampaign_filters() {
        UUID otherSheet = UUID.randomUUID();
        SheetActivityLog a = entry(sheet.getId(), ActivityCategory.ROLL, "2026-03-10T00:00:00Z");
        SheetActivityLog b = entry(sheet.getId(), ActivityCategory.RESOURCE, "2026-03-05T00:00:00Z");
        SheetActivityLog c = entry(otherSheet, ActivityCategory.ROLL, "2026-02-01T00:00:00Z");
        when(logRepository.findByCampaignIdOrderByCreatedAtDesc(campaign.getId())).thenReturn(List.of(a, b, c));
        when(userRepository.findAllById(any())).thenReturn(List.of(master));

        assertThat(service.listForCampaign(campaign.getId(), null, null, null, null, master.getId()))
                .hasSize(3);
        assertThat(service.listForCampaign(campaign.getId(), otherSheet, null, null, null, master.getId()))
                .extracting(SheetActivityLogResponse::id).containsExactly(c.getId());
        assertThat(service.listForCampaign(campaign.getId(), null, ActivityCategory.ROLL, null, null, master.getId()))
                .extracting(SheetActivityLogResponse::id).containsExactly(a.getId(), c.getId());
        assertThat(service.listForCampaign(campaign.getId(), null, null,
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-07T00:00:00Z"), master.getId()))
                .extracting(SheetActivityLogResponse::id).containsExactly(b.getId());
    }
}
