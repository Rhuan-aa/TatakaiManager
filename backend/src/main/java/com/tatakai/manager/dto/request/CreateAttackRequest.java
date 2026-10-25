package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.AttackActionKind;
import com.tatakai.manager.entity.BasicAttack;
import com.tatakai.manager.entity.RollSourceType;
import com.tatakai.manager.entity.StrikeSide;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateAttackRequest(
        @NotNull UUID attackerId,
        @NotNull RollSourceType sourceType,
        UUID sourceId,
        @Size(max = 100) @Pattern(regexp = "^[^<>]*$") String variation,
        BasicAttack basicAttack,
        @NotNull AttackActionKind actionKind,
        @NotEmpty @Size(max = 20) List<@NotNull UUID> targetIds,
        @Size(max = 40) @Pattern(regexp = "^[^<>]*$") String region,
        @Max(0) Integer regionPenalty,
        StrikeSide strikeSide) {

    public CreateAttackRequest(UUID attackerId, RollSourceType sourceType, UUID sourceId, String variation,
                               BasicAttack basicAttack, AttackActionKind actionKind, List<UUID> targetIds,
                               String region, Integer regionPenalty) {
        this(attackerId, sourceType, sourceId, variation, basicAttack, actionKind, targetIds, region, regionPenalty, null);
    }
}
