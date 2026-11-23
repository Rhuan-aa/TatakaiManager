package com.tatakai.manager.campaign;

import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.exception.CampaignNotFoundException;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.campaign.repository.CampaignRepository;
import com.tatakai.manager.campaign.service.CampaignDeletionService;
import com.tatakai.manager.shared.exception.AccessDeniedException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Exclusão de campanha")
class CampaignDeletionServiceTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private EntityManager em;
    @Mock private Query query;

    private final UUID cid = UUID.randomUUID(), uid = UUID.randomUUID();

    @Test
    @DisplayName("só o Mestre exclui")
    void soMestre() {
        var service = new CampaignDeletionService(campaignRepository, memberRepository, em);
        when(campaignRepository.existsById(cid)).thenReturn(true);
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(cid, uid, Role.MASTER)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.delete(cid, uid));
        verifyNoInteractions(em);
    }

    @Test
    @DisplayName("campanha inexistente é 404")
    void inexistente() {
        var service = new CampaignDeletionService(campaignRepository, memberRepository, em);
        assertThrows(CampaignNotFoundException.class, () -> service.delete(cid, uid));
    }

    @Test
    @DisplayName("Mestre apaga a campanha por último")
    void mestreApaga() {
        var service = new CampaignDeletionService(campaignRepository, memberRepository, em);
        when(campaignRepository.existsById(cid)).thenReturn(true);
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(cid, uid, Role.MASTER)).thenReturn(true);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        service.delete(cid, uid);
        verify(em).createNativeQuery("delete from campaigns where id = :c");
    }
}
