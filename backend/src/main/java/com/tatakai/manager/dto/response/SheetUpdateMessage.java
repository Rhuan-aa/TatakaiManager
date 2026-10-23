package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.ActivityCategory;

import java.util.UUID;

public record SheetUpdateMessage(SheetEvent event, UUID campaignId, UUID sheetId,
        ActivityCategory category, String action, String actorName) {

    public enum SheetEvent { SHEET_CREATED, SHEET_UPDATED, ROLL }
}
