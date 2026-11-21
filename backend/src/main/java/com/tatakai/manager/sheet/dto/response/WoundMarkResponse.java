package com.tatakai.manager.sheet.dto.response;

import com.tatakai.manager.sheet.dto.request.WoundGeometry;
import com.tatakai.manager.sheet.entity.BodyPart;
import com.tatakai.manager.sheet.entity.InjurySeverity;
import com.tatakai.manager.sheet.entity.WoundMark;
import com.tatakai.manager.sheet.entity.WoundType;

import java.time.Instant;
import java.util.UUID;

public record WoundMarkResponse(UUID id, BodyPart part, WoundType type, WoundGeometry geometry,
                                InjurySeverity severity, Instant createdAt) {
    public static WoundMarkResponse of(WoundMark w) {
        return new WoundMarkResponse(w.getId(), w.getPart(), w.getType(), w.getGeometry(), w.getSeverity(), w.getCreatedAt());
    }
}
