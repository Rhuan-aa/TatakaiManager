package com.tatakai.manager.combat.dto.request;

import com.tatakai.manager.combat.entity.AttackActionKind;
import com.tatakai.manager.combat.entity.BasicAttack;
import com.tatakai.manager.combat.entity.StrikeSide;
import com.tatakai.manager.combat.service.rules.GrappleRules;
import com.tatakai.manager.roll.entity.RollSourceType;

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
        StrikeSide strikeSide,
        /** Agarrão: ponto do agarre (padrão braço) e se aperta/torce no acerto (1d2 + FOR) (§39) */
        com.tatakai.manager.combat.service.rules.GrappleRules.Grip grip,
        Boolean squeeze,
        /** Ímpeto: metros em linha reta antes do golpe (§41) */
        @jakarta.validation.constraints.PositiveOrZero @Max(100) Double runMeters,
        /** Esmagar: há superfície sólida adjacente ao alvo (§41) */
        Boolean surface,
        /** Distância até o alvo em metros (disparo/arremesso, Grande, Dispersão; §42); null = não informada */
        @jakarta.validation.constraints.PositiveOrZero @Max(5000) Double distanceM) {

    public CreateAttackRequest(UUID attackerId, RollSourceType sourceType, UUID sourceId, String variation,
                               BasicAttack basicAttack, AttackActionKind actionKind, List<UUID> targetIds,
                               String region, Integer regionPenalty, StrikeSide strikeSide,
                               com.tatakai.manager.combat.service.rules.GrappleRules.Grip grip, Boolean squeeze,
                               Double runMeters, Boolean surface) {
        this(attackerId, sourceType, sourceId, variation, basicAttack, actionKind, targetIds, region, regionPenalty,
                strikeSide, grip, squeeze, runMeters, surface, null);
    }

    public CreateAttackRequest(UUID attackerId, RollSourceType sourceType, UUID sourceId, String variation,
                               BasicAttack basicAttack, AttackActionKind actionKind, List<UUID> targetIds,
                               String region, Integer regionPenalty, StrikeSide strikeSide,
                               com.tatakai.manager.combat.service.rules.GrappleRules.Grip grip, Boolean squeeze) {
        this(attackerId, sourceType, sourceId, variation, basicAttack, actionKind, targetIds, region, regionPenalty,
                strikeSide, grip, squeeze, null, null, null);
    }

    public CreateAttackRequest(UUID attackerId, RollSourceType sourceType, UUID sourceId, String variation,
                               BasicAttack basicAttack, AttackActionKind actionKind, List<UUID> targetIds,
                               String region, Integer regionPenalty, StrikeSide strikeSide) {
        this(attackerId, sourceType, sourceId, variation, basicAttack, actionKind, targetIds, region, regionPenalty,
                strikeSide, null, null, null, null, null);
    }

    public CreateAttackRequest(UUID attackerId, RollSourceType sourceType, UUID sourceId, String variation,
                               BasicAttack basicAttack, AttackActionKind actionKind, List<UUID> targetIds,
                               String region, Integer regionPenalty) {
        this(attackerId, sourceType, sourceId, variation, basicAttack, actionKind, targetIds, region, regionPenalty,
                null, null, null, null, null, null);
    }
}
