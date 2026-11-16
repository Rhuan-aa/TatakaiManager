package com.tatakai.manager.sheet.repository;

import com.tatakai.manager.sheet.entity.SheetImage;
import com.tatakai.manager.sheet.entity.SheetImageKind;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SheetImageRepository extends JpaRepository<SheetImage, UUID> {

    /** Par (ficha, tipo) sem carregar os bytes. */
    record Key(UUID sheetId, SheetImageKind kind) {}

    Optional<SheetImage> findBySheetIdAndKind(UUID sheetId, SheetImageKind kind);

    void deleteBySheetIdAndKind(UUID sheetId, SheetImageKind kind);

    @Query("select new com.tatakai.manager.sheet.repository.SheetImageRepository$Key(i.sheetId, i.kind) "
            + "from SheetImage i where i.sheetId in :ids")
    List<Key> findKeys(@Param("ids") Collection<UUID> ids);
}
