package com.tatakai.manager.timeskip.repository;

import com.tatakai.manager.timeskip.entity.SubmissionStatus;
import com.tatakai.manager.timeskip.entity.TimeSkipSubmission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeSkipSubmissionRepository extends JpaRepository<TimeSkipSubmission, UUID> {

    Optional<TimeSkipSubmission> findByTimeSkipIdAndUserId(UUID timeSkipId, UUID userId);

    List<TimeSkipSubmission> findByTimeSkipId(UUID timeSkipId);

    List<TimeSkipSubmission> findByTimeSkipIdInAndStatus(Collection<UUID> timeSkipIds, SubmissionStatus status);
}
