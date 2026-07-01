package com.tatakai.manager.repository;

import com.tatakai.manager.entity.CharacterSheetVisibility;
import com.tatakai.manager.entity.SheetSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CharacterSheetVisibilityRepository
        extends JpaRepository<CharacterSheetVisibility, UUID> {

    List<CharacterSheetVisibility> findByCampaignMemberId(UUID campaignMemberId);

    Optional<CharacterSheetVisibility> findByCampaignMemberIdAndSection(
            UUID campaignMemberId, SheetSection section);
}
