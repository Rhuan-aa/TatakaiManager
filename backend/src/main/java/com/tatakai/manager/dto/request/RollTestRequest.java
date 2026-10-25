package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.BasicAttack;
import com.tatakai.manager.entity.RollChannel;
import com.tatakai.manager.entity.RollMode;
import com.tatakai.manager.entity.RollSourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record RollTestRequest(
        @NotNull RollSourceType sourceType,
        UUID sourceId,
        @Size(max = 100) @Pattern(regexp = "^[^<>]*$") String variation,
        BasicAttack basicAttack,
        Attribute attribute,
        @NotNull RollMode mode,
        RollChannel channel,
        UUID campaignId,
        UUID customChannelId,
        @Size(max = 200) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String purpose,
        @Min(-3) @Max(3) int advantage,
        UUID baseId,
        boolean targetDowned,
        boolean attackerDowned,
        @Size(max = 40) @Pattern(regexp = "^[^<>]*$") String localizedRegion,
        @Max(0) Integer localizedPenalty,
        @Valid @Size(max = 20) List<RollOverride> overrides,
        @Size(max = 200) @Pattern(regexp = "^[^<>]*$") String editReason
) {}
