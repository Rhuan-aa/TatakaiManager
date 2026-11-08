package com.tatakai.manager.combat.dto.response;

import com.tatakai.manager.combat.entity.AttackActionKind;
import com.tatakai.manager.combat.entity.AttackOutcome;
import com.tatakai.manager.combat.entity.AttackStatus;
import com.tatakai.manager.combat.entity.LateCounter;
import com.tatakai.manager.combat.entity.ReactionType;
import com.tatakai.manager.roll.dto.response.RollMessage;

import java.util.List;
import java.util.UUID;

public record AttackResponse(UUID id, UUID encounterId, UUID attackerId, String attackerName, String sourceName,
        AttackActionKind actionKind, String region, int reservedPa, int reservedSp, AttackStatus status, int round,
        RollMessage attackRoll, List<TargetResponse> targets,
        /** Encadear (§41): o atacante pode abortar e trocar de técnica agora. */
        boolean canChain) {

    public record TargetResponse(UUID id, UUID targetId, String targetName, ReactionType reaction,
            boolean autoResolved, boolean canRespond, List<Option> options, RollMessage reactionRoll,
            AttackOutcome outcome, Integer mitigationPct, Integer damage, Integer counterDamage,
            LateCounter lateCounter, Integer lateCounterDamage, boolean canLateCounter,
            UUID defenderId, String defenderName, List<Guardian> guardians, List<TechOption> techniques,
            String reactionTechniqueName, RollMessage lateCounterRoll) {}

    /** Técnica de núcleo Reação disponível ao alvo (§38): substitui a reação do tipo dela. */
    public record TechOption(UUID techniqueId, String variation, String name, ReactionType reaction, boolean available,
                             String reason, int predictedBonus, int spCost, List<String> triggers) {}

    /** Aliado que pode reagir no lugar de um alvo Inconsciente, com as opções calculadas com a ficha dele. */
    public record Guardian(UUID id, String name, List<Option> options) {}

    public record Option(ReactionType reaction, boolean available, String reason, int predictedBonus) {}
}
