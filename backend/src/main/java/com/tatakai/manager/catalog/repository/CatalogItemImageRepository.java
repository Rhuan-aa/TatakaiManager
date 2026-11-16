package com.tatakai.manager.catalog.repository;

import com.tatakai.manager.catalog.entity.CatalogItemImage;
import com.tatakai.manager.catalog.entity.CatalogItemKind;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogItemImageRepository extends JpaRepository<CatalogItemImage, UUID> {

    /** Tipo e chave sem carregar os bytes. */
    record Key(CatalogItemKind kind, String itemKey) {}

    Optional<CatalogItemImage> findByKindAndItemKeyAndCampaignIdIsNull(CatalogItemKind kind, String itemKey);

    Optional<CatalogItemImage> findByKindAndItemKeyAndCampaignId(CatalogItemKind kind, String itemKey, UUID campaignId);

    void deleteByKindAndItemKeyAndCampaignIdIsNull(CatalogItemKind kind, String itemKey);

    void deleteByKindAndItemKeyAndCampaignId(CatalogItemKind kind, String itemKey, UUID campaignId);

    @Query("select new com.tatakai.manager.catalog.repository.CatalogItemImageRepository$Key(i.kind, i.itemKey) "
            + "from CatalogItemImage i where i.campaignId is null")
    List<Key> findGlobalKeys();

    @Query("select new com.tatakai.manager.catalog.repository.CatalogItemImageRepository$Key(i.kind, i.itemKey) "
            + "from CatalogItemImage i where i.campaignId = :cid")
    List<Key> findCampaignKeys(@Param("cid") UUID campaignId);
}
