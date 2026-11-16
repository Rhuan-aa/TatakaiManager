package com.tatakai.manager.catalog.service;

import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.catalog.dto.response.CatalogItemImageResponse;
import com.tatakai.manager.catalog.entity.CatalogItemImage;
import com.tatakai.manager.catalog.entity.CatalogItemKind;
import com.tatakai.manager.catalog.exception.CatalogItemNotFoundException;
import com.tatakai.manager.catalog.repository.CatalogItemImageRepository;
import com.tatakai.manager.catalog.service.rules.ImageThumbs;
import com.tatakai.manager.npc.exception.InvalidImageException;
import com.tatakai.manager.shared.exception.AccessDeniedException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Imagem opcional dos itens do catálogo estático (armas, equipamentos, consumíveis). */
@Service
public class CatalogItemImageService {

    static final int MAX_BYTES = 5 * 1024 * 1024;

    private final CatalogItemImageRepository repository;
    private final CampaignMemberRepository memberRepository;
    private final CatalogService catalogService;
    private final WeaponCatalog weapons;
    private final EquipmentCatalog equipment;
    private final ConsumableCatalog consumables;

    public CatalogItemImageService(CatalogItemImageRepository repository, CampaignMemberRepository memberRepository,
                                   CatalogService catalogService, WeaponCatalog weapons,
                                   EquipmentCatalog equipment, ConsumableCatalog consumables) {
        this.repository = repository;
        this.memberRepository = memberRepository;
        this.catalogService = catalogService;
        this.weapons = weapons;
        this.equipment = equipment;
        this.consumables = consumables;
    }

    /** campaignId nulo = global. */
    @Transactional
    public void set(CatalogItemKind kind, String key, UUID campaignId, UUID userId, byte[] bytes, String contentType) {
        requireWrite(campaignId, userId);
        requireItem(kind, key);
        if (bytes == null || bytes.length == 0) throw new InvalidImageException("O arquivo de imagem está vazio");
        if (bytes.length > MAX_BYTES) throw new InvalidImageException("A imagem excede o limite de 5 MB");
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidImageException("O arquivo enviado não é uma imagem");
        }
        CatalogItemImage image = find(kind, key, campaignId).orElseGet(() ->
                CatalogItemImage.builder().kind(kind).itemKey(key).campaignId(campaignId).build());
        ImageThumbs.Thumb thumb = ImageThumbs.of(bytes, contentType);
        image.setContentType(contentType);
        image.setData(bytes);
        image.setThumbContentType(thumb.contentType());
        image.setThumb(thumb.data());
        repository.save(image);
    }

    @Transactional
    public void delete(CatalogItemKind kind, String key, UUID campaignId, UUID userId) {
        requireWrite(campaignId, userId);
        requireItem(kind, key);
        if (campaignId == null) repository.deleteByKindAndItemKeyAndCampaignIdIsNull(kind, key);
        else repository.deleteByKindAndItemKeyAndCampaignId(kind, key, campaignId);
    }

    /** Campanha: a da campanha, senão a global (membro obrigatório). Global: qualquer autenticado. */
    @Transactional(readOnly = true)
    public CatalogItemImage get(CatalogItemKind kind, String key, UUID campaignId, UUID userId) {
        if (campaignId != null) requireMember(campaignId, userId);
        requireItem(kind, key);
        if (campaignId != null) {
            var own = repository.findByKindAndItemKeyAndCampaignId(kind, key, campaignId);
            if (own.isPresent()) return own.get();
        }
        return find(kind, key, null).orElseThrow(() -> new CatalogItemNotFoundException("Item sem imagem"));
    }

    @Transactional(readOnly = true)
    public List<CatalogItemImageResponse> listGlobal() {
        return repository.findGlobalKeys().stream()
                .map(k -> new CatalogItemImageResponse(k.kind(), k.itemKey(), null)).toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogItemImageResponse> listForCampaign(UUID campaignId, UUID userId) {
        requireMember(campaignId, userId);
        List<CatalogItemImageResponse> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (var k : repository.findCampaignKeys(campaignId)) {
            seen.add(k.kind() + ":" + k.itemKey());
            out.add(new CatalogItemImageResponse(k.kind(), k.itemKey(), "CAMPAIGN"));
        }
        for (var k : repository.findGlobalKeys()) {
            if (seen.add(k.kind() + ":" + k.itemKey())) {
                out.add(new CatalogItemImageResponse(k.kind(), k.itemKey(), "GLOBAL"));
            }
        }
        return out;
    }

    private Optional<CatalogItemImage> find(CatalogItemKind kind, String key, UUID campaignId) {
        return campaignId == null ? repository.findByKindAndItemKeyAndCampaignIdIsNull(kind, key)
                : repository.findByKindAndItemKeyAndCampaignId(kind, key, campaignId);
    }

    private void requireItem(CatalogItemKind kind, String key) {
        boolean exists = switch (kind) {
            case WEAPON -> weapons.find(key).isPresent();
            case EQUIPMENT -> equipment.find(key).isPresent();
            case CONSUMABLE -> consumables.find(key).isPresent();
        };
        if (!exists) throw new CatalogItemNotFoundException("Item do catálogo não encontrado");
    }

    private void requireWrite(UUID campaignId, UUID userId) {
        if (campaignId == null) {
            if (!catalogService.isGlobalEditor(userId)) {
                throw new AccessDeniedException("Apenas editores do catálogo global podem realizar esta ação");
            }
        } else if (!memberRepository.existsByCampaignIdAndUserIdAndRole(campaignId, userId, Role.MASTER)) {
            throw new AccessDeniedException("Apenas o Mestre da campanha pode realizar esta ação");
        }
    }

    private void requireMember(UUID campaignId, UUID userId) {
        if (!memberRepository.existsByCampaignIdAndUserId(campaignId, userId)) {
            throw new AccessDeniedException("Você não é membro desta campanha");
        }
    }
}
