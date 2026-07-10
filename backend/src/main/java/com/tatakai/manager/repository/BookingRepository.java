package com.tatakai.manager.repository;

import com.tatakai.manager.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /** Reservas do NPC no dia — usadas para checar sobreposição de faixas de slots. */
    List<Booking> findByTimeSkipDayIdAndNpcId(UUID timeSkipDayId, UUID npcId);

    /** Reservas solo do jogador no dia — o mesmo jogador não pode sobrepor as próprias faixas. */
    List<Booking> findByTimeSkipDayIdAndUserIdAndNpcIdIsNull(UUID timeSkipDayId, UUID userId);

    /** Slot extra: cada jogador só pode ter uma ação de custo zero por dia (NPC ou solo). */
    boolean existsByTimeSkipDayIdAndUserIdAndSlotNumber(UUID timeSkipDayId, UUID userId, short slotNumber);

    List<Booking> findByTimeSkipDay_TimeSkipId(UUID timeSkipId);

    List<Booking> findByTimeSkipDay_TimeSkipIdAndNpcId(UUID timeSkipId, UUID npcId);

    /** Agendamentos de um NPC em toda a campanha (usado ao remover o NPC da campanha). */
    List<Booking> findByNpcIdAndTimeSkipDay_TimeSkip_CampaignId(UUID npcId, UUID campaignId);
}
