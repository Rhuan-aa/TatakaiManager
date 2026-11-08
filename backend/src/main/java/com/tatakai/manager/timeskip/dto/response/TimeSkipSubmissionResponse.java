package com.tatakai.manager.timeskip.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TimeSkipSubmissionResponse(UUID userId, String userName, String status, String note,
                                         Instant submittedAt, Instant reviewedAt) {}
