package com.tatakai.manager.sheet.dto.response;

import com.tatakai.manager.sheet.entity.ActivityCategory;

import java.util.UUID;

public record SheetUpdateMessage(SheetEvent event, UUID campaignId, UUID sheetId,
        ActivityCategory category, String action, String actorName) {

    public enum SheetEvent { SHEET_CREATED, SHEET_UPDATED, ROLL }
}
