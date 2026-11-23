package com.tatakai.manager.sheet.dto.response;

import com.tatakai.manager.sheet.dto.request.GmAdjustmentRequest;
import com.tatakai.manager.sheet.entity.SheetChangeStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChangeRequestResponse(UUID id, UUID sheetId, UUID requesterId, SheetChangeStatus status,
                                    List<GmAdjustmentRequest> changes, List<String> summary, String reviewNote,
                                    Instant createdAt, Instant reviewedAt) {}
