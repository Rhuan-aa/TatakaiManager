package com.tatakai.manager.campaign.service;

import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.exception.CampaignNotFoundException;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.campaign.repository.CampaignRepository;
import com.tatakai.manager.shared.exception.AccessDeniedException;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Exclusão da campanha e de tudo que depende dela. NPCs do acervo (e seus dados no Mongo) permanecem. */
@Service
public class CampaignDeletionService {

    private static final String SHEETS = "(select s.id from character_sheets s join campaign_members m "
            + "on s.campaign_member_id = m.id where m.campaign_id = :c)";
    private static final String ENC = "(select id from encounters where campaign_id = :c)";
    private static final String PART = "(select id from encounter_participants where encounter_id in " + ENC + ")";
    private static final String TS = "(select id from time_skips where campaign_id = :c)";
    private static final String DAYS = "(select id from time_skip_days where time_skip_id in " + TS + ")";
    private static final String MEMBERS = "(select id from campaign_members where campaign_id = :c)";

    /** Ordem importa: filhos antes dos pais (FKs). */
    private static final List<String> STATEMENTS = List.of(
            "delete from attack_targets where attack_id in (select id from pending_attacks where encounter_id in " + ENC + ")",
            "delete from pending_attacks where encounter_id in " + ENC,
            "delete from combat_conditions where participant_id in " + PART,
            "delete from combat_counters where participant_id in " + PART,
            "delete from combat_events where encounter_id in " + ENC,
            "delete from encounter_participants where encounter_id in " + ENC,
            "delete from encounters where campaign_id = :c",
            "delete from training_entries where sheet_id in " + SHEETS + " or time_skip_id in " + TS,
            "delete from fight_records where campaign_id = :c or sheet_id in " + SHEETS,
            "delete from interaction_logs where campaign_id = :c",
            "delete from bookings where time_skip_day_id in " + DAYS,
            "delete from time_skip_submissions where time_skip_id in " + TS,
            "delete from time_skip_activities where time_skip_id in " + TS,
            "delete from time_skip_days where time_skip_id in " + TS,
            "delete from time_skips where campaign_id = :c",
            "delete from character_techniques where sheet_id in " + SHEETS,
            "delete from character_arts where sheet_id in " + SHEETS,
            "delete from notebook_drafts where sheet_id in " + SHEETS,
            "delete from forges where sheet_id in " + SHEETS,
            "delete from character_instincts where sheet_id in " + SHEETS,
            "delete from overcome_distributions where sheet_id in " + SHEETS,
            "delete from rivals where sheet_id in " + SHEETS,
            "delete from sheet_images where sheet_id in " + SHEETS,
            "delete from sheet_activity_logs where campaign_id = :c or sheet_id in " + SHEETS,
            "delete from sheet_change_requests where sheet_id in " + SHEETS,
            "delete from character_builds where sheet_id in " + SHEETS,
            "delete from permanent_injuries where sheet_id in " + SHEETS,
            "delete from inventory_items where sheet_id in " + SHEETS,
            "delete from wound_marks where sheet_id in " + SHEETS,
            "delete from character_traits where sheet_id in " + SHEETS,
            "delete from character_clans where sheet_id in " + SHEETS,
            "delete from rolls where campaign_id = :c or sheet_id in " + SHEETS,
            "delete from roll_channels where campaign_id = :c",
            "delete from character_sheets where campaign_member_id in " + MEMBERS,
            "delete from character_sheet_visibility where campaign_member_id in " + MEMBERS,
            "delete from npc_sheet_visibility where campaign_npc_id in (select id from campaign_npcs where campaign_id = :c)",
            "delete from campaign_npcs where campaign_id = :c",
            "delete from campaign_rule_settings where campaign_id = :c",
            "delete from catalog_item_images where campaign_id = :c",
            "delete from campaign_members where campaign_id = :c",
            "delete from campaigns where id = :c");

    private final CampaignRepository campaignRepository;
    private final CampaignMemberRepository memberRepository;
    private final EntityManager em;

    public CampaignDeletionService(CampaignRepository campaignRepository, CampaignMemberRepository memberRepository,
                                   EntityManager em) {
        this.campaignRepository = campaignRepository;
        this.memberRepository = memberRepository;
        this.em = em;
    }

    @Transactional
    public void delete(UUID campaignId, UUID requesterId) {
        if (!campaignRepository.existsById(campaignId)) throw new CampaignNotFoundException();
        if (!memberRepository.existsByCampaignIdAndUserIdAndRole(campaignId, requesterId, Role.MASTER)) {
            throw new AccessDeniedException("Apenas o Mestre pode excluir a campanha");
        }
        for (String sql : STATEMENTS) {
            em.createNativeQuery(sql).setParameter("c", campaignId).executeUpdate();
        }
        em.clear();
    }
}
