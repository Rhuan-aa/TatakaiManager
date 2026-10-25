package com.tatakai.manager.campaign.repository;

import com.tatakai.manager.campaign.entity.Campaign;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
}
