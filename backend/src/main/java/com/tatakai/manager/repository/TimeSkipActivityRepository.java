package com.tatakai.manager.repository;

import com.tatakai.manager.entity.TimeSkipActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeSkipActivityRepository extends JpaRepository<TimeSkipActivity, UUID> {

    List<TimeSkipActivity> findByTimeSkipIdOrderByCreatedAt(UUID timeSkipId);

    Optional<TimeSkipActivity> findByIdAndTimeSkipId(UUID id, UUID timeSkipId);
}
