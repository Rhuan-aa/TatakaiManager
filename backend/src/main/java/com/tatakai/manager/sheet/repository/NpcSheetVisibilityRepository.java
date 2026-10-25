package com.tatakai.manager.sheet.repository;

import com.tatakai.manager.sheet.entity.NpcSheetVisibility;
import com.tatakai.manager.sheet.entity.SheetSection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NpcSheetVisibilityRepository extends JpaRepository<NpcSheetVisibility, UUID> {

    List<NpcSheetVisibility> findByCampaignNpcId(UUID campaignNpcId);

    Optional<NpcSheetVisibility> findByCampaignNpcIdAndSection(UUID campaignNpcId, SheetSection section);

    @Modifying
    @Transactional
    void deleteByCampaignNpcId(UUID campaignNpcId);
}
