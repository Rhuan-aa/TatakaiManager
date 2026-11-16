package com.tatakai.manager.catalog;

import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.catalog.entity.CatalogItemImage;
import com.tatakai.manager.catalog.entity.CatalogItemKind;
import com.tatakai.manager.catalog.exception.CatalogItemNotFoundException;
import com.tatakai.manager.catalog.repository.CatalogItemImageRepository;
import com.tatakai.manager.catalog.repository.CatalogItemImageRepository.Key;
import com.tatakai.manager.catalog.service.CatalogItemImageService;
import com.tatakai.manager.catalog.service.CatalogService;
import com.tatakai.manager.catalog.service.ConsumableCatalog;
import com.tatakai.manager.catalog.service.EquipmentCatalog;
import com.tatakai.manager.catalog.service.WeaponCatalog;
import com.tatakai.manager.npc.exception.InvalidImageException;
import com.tatakai.manager.shared.exception.AccessDeniedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Imagens dos itens do catálogo")
class CatalogItemImageServiceTest {

    @Mock private CatalogItemImageRepository repository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private CatalogService catalogService;

    private CatalogItemImageService service;
    private final UUID user = UUID.randomUUID();
    private final UUID cid = UUID.randomUUID();
    private static final byte[] IMG = {1, 2, 3};
    private static final CatalogItemKind W = CatalogItemKind.WEAPON;

    @BeforeEach
    void setUp() {
        service = new CatalogItemImageService(repository, memberRepository, catalogService,
                new WeaponCatalog(), new EquipmentCatalog(), new ConsumableCatalog());
    }

    private static CatalogItemImage img(UUID campaignId) {
        return CatalogItemImage.builder().kind(W).itemKey("GARRAFA").campaignId(campaignId)
                .contentType("image/png").data(IMG).thumbContentType("image/png").thumb(IMG).build();
    }

    @Test
    @DisplayName("editor global envia imagem global com miniatura")
    void editorUploadsGlobal() {
        when(catalogService.isGlobalEditor(user)).thenReturn(true);
        when(repository.findByKindAndItemKeyAndCampaignIdIsNull(W, "GARRAFA")).thenReturn(Optional.empty());
        service.set(W, "GARRAFA", null, user, IMG, "image/png");
        ArgumentCaptor<CatalogItemImage> cap = ArgumentCaptor.forClass(CatalogItemImage.class);
        verify(repository).save(cap.capture());
        assertNull(cap.getValue().getCampaignId());
        assertNotNull(cap.getValue().getThumb());
    }

    @Test
    @DisplayName("não-editor não envia imagem global (403)")
    void nonEditorForbidden() {
        when(catalogService.isGlobalEditor(user)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.set(W, "GARRAFA", null, user, IMG, "image/png"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Mestre envia imagem da campanha")
    void masterUploadsCampaign() {
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(cid, user, Role.MASTER)).thenReturn(true);
        when(repository.findByKindAndItemKeyAndCampaignId(W, "GARRAFA", cid)).thenReturn(Optional.empty());
        service.set(CatalogItemKind.WEAPON, "GARRAFA", cid, user, IMG, "image/png");
        verify(repository).save(any());
    }

    @Test
    @DisplayName("jogador não envia imagem da campanha (403)")
    void playerForbidden() {
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(cid, user, Role.MASTER)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.set(W, "GARRAFA", cid, user, IMG, "image/png"));
    }

    @Test
    @DisplayName("arquivo vazio, grande ou não-imagem é rejeitado")
    void invalidFiles() {
        when(catalogService.isGlobalEditor(user)).thenReturn(true);
        assertThrows(InvalidImageException.class, () -> service.set(W, "GARRAFA", null, user, new byte[0], "image/png"));
        assertThrows(InvalidImageException.class,
                () -> service.set(W, "GARRAFA", null, user, new byte[5 * 1024 * 1024 + 1], "image/png"));
        assertThrows(InvalidImageException.class, () -> service.set(W, "GARRAFA", null, user, IMG, "text/plain"));
    }

    @Test
    @DisplayName("chave desconhecida dá 404")
    void unknownKey() {
        when(catalogService.isGlobalEditor(user)).thenReturn(true);
        assertThrows(CatalogItemNotFoundException.class, () -> service.set(W, "NAO_EXISTE", null, user, IMG, "image/png"));
    }

    @Test
    @DisplayName("envio substitui a linha existente em vez de criar outra")
    void replacesExisting() {
        CatalogItemImage existing = img(null);
        when(catalogService.isGlobalEditor(user)).thenReturn(true);
        when(repository.findByKindAndItemKeyAndCampaignIdIsNull(W, "GARRAFA")).thenReturn(Optional.of(existing));
        service.set(W, "GARRAFA", null, user, new byte[]{9, 9}, "image/jpeg");
        verify(repository).save(existing);
        assertEquals("image/jpeg", existing.getContentType());
        assertArrayEquals(new byte[]{9, 9}, existing.getData());
    }

    @Test
    @DisplayName("GET da campanha cai na imagem global quando não há da campanha")
    void fallsBackToGlobal() {
        CatalogItemImage global = img(null);
        when(memberRepository.existsByCampaignIdAndUserId(cid, user)).thenReturn(true);
        when(repository.findByKindAndItemKeyAndCampaignId(W, "GARRAFA", cid)).thenReturn(Optional.empty());
        when(repository.findByKindAndItemKeyAndCampaignIdIsNull(W, "GARRAFA")).thenReturn(Optional.of(global));
        assertSame(global, service.get(W, "GARRAFA", cid, user));
    }

    @Test
    @DisplayName("imagem da campanha vence a global")
    void campaignWins() {
        CatalogItemImage own = img(cid);
        when(memberRepository.existsByCampaignIdAndUserId(cid, user)).thenReturn(true);
        when(repository.findByKindAndItemKeyAndCampaignId(W, "GARRAFA", cid)).thenReturn(Optional.of(own));
        assertSame(own, service.get(W, "GARRAFA", cid, user));
    }

    @Test
    @DisplayName("não-membro não lê imagem da campanha (403); sem nenhuma imagem dá 404")
    void readRules() {
        when(memberRepository.existsByCampaignIdAndUserId(cid, user)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.get(W, "GARRAFA", cid, user));
        when(repository.findByKindAndItemKeyAndCampaignIdIsNull(W, "GARRAFA")).thenReturn(Optional.empty());
        assertThrows(CatalogItemNotFoundException.class, () -> service.get(W, "GARRAFA", null, user));
    }

    @Test
    @DisplayName("listagem da campanha une escopos e a campanha vence")
    void listingMerges() {
        when(memberRepository.existsByCampaignIdAndUserId(cid, user)).thenReturn(true);
        when(repository.findCampaignKeys(cid)).thenReturn(List.of(new Key(W, "A")));
        when(repository.findGlobalKeys()).thenReturn(List.of(new Key(W, "A"), new Key(CatalogItemKind.CONSUMABLE, "B")));
        var out = service.listForCampaign(cid, user);
        assertEquals(2, out.size());
        assertEquals("CAMPAIGN", out.stream().filter(r -> r.key().equals("A")).findFirst().orElseThrow().scope());
        assertEquals("GLOBAL", out.stream().filter(r -> r.key().equals("B")).findFirst().orElseThrow().scope());
    }
}
