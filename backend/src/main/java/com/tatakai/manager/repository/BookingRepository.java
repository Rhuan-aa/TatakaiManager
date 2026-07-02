package com.tatakai.manager.repository;

import com.tatakai.manager.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    boolean existsByTimeSkipDayIdAndNpcIdAndSlotNumber(UUID timeSkipDayId, UUID npcId, short slotNumber);

    List<Booking> findByTimeSkipDay_TimeSkipId(UUID timeSkipId);

    List<Booking> findByTimeSkipDay_TimeSkipIdAndNpcId(UUID timeSkipId, UUID npcId);

    /** Agendamentos de um NPC em toda a campanha (usado ao remover o NPC da campanha). */
    List<Booking> findByNpcIdAndTimeSkipDay_TimeSkip_CampaignId(UUID npcId, UUID campaignId);
}
