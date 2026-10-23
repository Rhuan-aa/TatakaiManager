package com.tatakai.manager.repository;

import com.tatakai.manager.entity.CharacterSheet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CharacterSheetRepository extends JpaRepository<CharacterSheet, UUID> {

    Optional<CharacterSheet> findByCampaignMemberId(UUID campaignMemberId);

    boolean existsByCampaignMemberId(UUID campaignMemberId);

    List<CharacterSheet> findByCampaignMemberCampaignId(UUID campaignId);

    Optional<CharacterSheet> findByNpcId(UUID npcId);

    boolean existsByNpcId(UUID npcId);

    List<CharacterSheet> findByNpcIdIn(Collection<UUID> npcIds);
}
