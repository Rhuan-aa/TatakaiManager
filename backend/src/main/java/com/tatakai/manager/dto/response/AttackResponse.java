package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.AttackActionKind;
import com.tatakai.manager.entity.AttackOutcome;
import com.tatakai.manager.entity.AttackStatus;
import com.tatakai.manager.entity.LateCounter;
import com.tatakai.manager.entity.ReactionType;

import java.util.List;
import java.util.UUID;

public record AttackResponse(UUID id, UUID encounterId, UUID attackerId, String attackerName, String sourceName,
        AttackActionKind actionKind, String region, int reservedPa, int reservedSp, AttackStatus status, int round,
        RollMessage attackRoll, List<TargetResponse> targets) {

    public record TargetResponse(UUID id, UUID targetId, String targetName, ReactionType reaction,
            boolean autoResolved, boolean canRespond, List<Option> options, RollMessage reactionRoll,
            AttackOutcome outcome, Integer mitigationPct, Integer damage, Integer counterDamage,
            LateCounter lateCounter, Integer lateCounterDamage, boolean canLateCounter,
            UUID defenderId, String defenderName, List<Guardian> guardians) {}

    /** Aliado que pode reagir no lugar de um alvo Inconsciente, com as opções calculadas com a ficha dele. */
    public record Guardian(UUID id, String name, List<Option> options) {}

    public record Option(ReactionType reaction, boolean available, String reason, int predictedBonus) {}
}
