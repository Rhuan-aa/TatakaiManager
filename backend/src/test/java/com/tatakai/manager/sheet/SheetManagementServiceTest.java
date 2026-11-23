package com.tatakai.manager.sheet;

import com.tatakai.manager.auth.entity.User;
import com.tatakai.manager.build.entity.BuildStatus;
import com.tatakai.manager.campaign.entity.Campaign;
import com.tatakai.manager.campaign.entity.CampaignMember;
import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.shared.exception.AccessDeniedException;
import com.tatakai.manager.sheet.dto.request.CreateChangeRequest;
import com.tatakai.manager.sheet.dto.request.GmAdjustmentRequest;
import com.tatakai.manager.sheet.dto.request.ReviewChangeRequest;
import com.tatakai.manager.sheet.entity.CharacterSheet;
import com.tatakai.manager.sheet.entity.GmAction;
import com.tatakai.manager.sheet.entity.SheetChangeRequest;
import com.tatakai.manager.sheet.entity.SheetChangeStatus;
import com.tatakai.manager.sheet.exception.InvalidCharacterSheetException;
import com.tatakai.manager.sheet.repository.CharacterSheetRepository;
import com.tatakai.manager.sheet.repository.SheetChangeRequestRepository;
import com.tatakai.manager.sheet.service.CharacterSheetService;
import com.tatakai.manager.sheet.service.SheetManagementService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Exclusão de ficha e pedidos de ajuste")
class SheetManagementServiceTest {

    @Mock private CharacterSheetRepository sheetRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private SheetChangeRequestRepository changeRepository;
    @Mock private CharacterSheetService sheetService;
    @Mock private ApplicationEventPublisher events;
    @Mock private EntityManager em;
    @Mock private Query query;
    @Mock private com.tatakai.manager.sheet.service.SheetActivityLogService activityLog;

    private SheetManagementService service;
    private User master, owner, other;
    private CharacterSheet sheet;
    private final GmAdjustmentRequest change = new GmAdjustmentRequest(GmAction.GRANT_FIGHT_EXPERIENCE, null, 2, "bônus", null, null, null);

    @BeforeEach
    void setUp() {
        service = new SheetManagementService(sheetRepository, memberRepository, changeRepository, sheetService, events, em, activityLog);
        Campaign campaign = Campaign.builder().id(UUID.randomUUID()).build();
        master = User.builder().id(UUID.randomUUID()).build();
        owner = User.builder().id(UUID.randomUUID()).build();
        other = User.builder().id(UUID.randomUUID()).build();
        member(campaign, master, Role.MASTER);
        CampaignMember om = member(campaign, owner, Role.PLAYER);
        member(campaign, other, Role.PLAYER);
        sheet = CharacterSheet.builder().id(UUID.randomUUID()).campaignMember(om).buildStatus(BuildStatus.COMPLETED).build();
        lenient().when(sheetRepository.findById(sheet.getId())).thenReturn(Optional.of(sheet));
    }

    private CampaignMember member(Campaign c, User u, Role r) {
        CampaignMember m = CampaignMember.builder().id(UUID.randomUUID()).campaign(c).user(u).role(r).build();
        lenient().when(memberRepository.findByCampaignIdAndUserId(c.getId(), u.getId())).thenReturn(Optional.of(m));
        return m;
    }

    @Test
    @DisplayName("dono e Mestre excluem; outro jogador não")
    void delete() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);
        assertThrows(AccessDeniedException.class, () -> service.delete(sheet.getId(), other.getId()));
        service.delete(sheet.getId(), owner.getId());
        service.delete(sheet.getId(), master.getId());
        verify(sheetRepository, times(2)).delete(sheet);
    }

    @Test
    @DisplayName("ficha em combate não é excluída")
    void deleteInCombat() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1L);
        assertThrows(InvalidCharacterSheetException.class, () -> service.delete(sheet.getId(), owner.getId()));
        verify(sheetRepository, never()).delete(any());
    }

    @Test
    @DisplayName("só o dono propõe; Mestre aprova aplicando os ajustes")
    void proposeAndApprove() {
        when(changeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThrows(AccessDeniedException.class,
                () -> service.propose(sheet.getId(), new CreateChangeRequest(List.of(change)), master.getId()));
        service.propose(sheet.getId(), new CreateChangeRequest(List.of(change)), owner.getId());

        SheetChangeRequest cr = SheetChangeRequest.builder().id(UUID.randomUUID()).sheetId(sheet.getId())
                .requesterId(owner.getId()).status(SheetChangeStatus.PENDING).changes(List.of(change)).build();
        when(changeRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        assertThrows(AccessDeniedException.class,
                () -> service.review(sheet.getId(), cr.getId(), new ReviewChangeRequest(true, null), owner.getId()));
        service.review(sheet.getId(), cr.getId(), new ReviewChangeRequest(true, null), master.getId());
        verify(sheetService).applyGmAdjustment(sheet.getId(), change, master.getId());
        assertEquals(SheetChangeStatus.APPROVED, cr.getStatus());
        assertThrows(InvalidCharacterSheetException.class,
                () -> service.review(sheet.getId(), cr.getId(), new ReviewChangeRequest(false, null), master.getId()));
    }
}
