package com.tatakai.manager.repository;

import com.tatakai.manager.entity.Booking;
import com.tatakai.manager.entity.InteractionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InteractionLogRepository extends JpaRepository<InteractionLog, UUID> {

    List<InteractionLog> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId);

    Optional<InteractionLog> findByIdAndCampaignId(UUID id, UUID campaignId);

    /** Remove os logs vinculados a estes agendamentos (usado ao excluir um TimeSkip). */
    void deleteByBookingIn(List<Booking> bookings);
}
