package com.tatakai.manager.repository;

import com.tatakai.manager.entity.TimeSkipDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TimeSkipDayRepository extends JpaRepository<TimeSkipDay, UUID> {

    Optional<TimeSkipDay> findByTimeSkipIdAndDayNumber(UUID timeSkipId, short dayNumber);
}
