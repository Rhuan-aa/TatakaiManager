package com.tatakai.manager.service.rules;

import com.tatakai.manager.entity.AttackActionKind;
import com.tatakai.manager.entity.AttackOutcome;
import com.tatakai.manager.entity.ReactionType;

/** Regras puras da transação de ataque/reação (F6b). Sem Spring, sem aleatoriedade. */
public final class AttackRules {

    private AttackRules() {
    }

    /** MAJOR 3 PA (+1 se Lenta); MINOR_OFFENSIVE 2 PA. */
    public static int paCost(AttackActionKind kind, boolean slow) {
        return kind == AttackActionKind.MAJOR ? (slow ? 4 : 3) : 2;
    }

    /** Luta contra Grupo: −(n_atacantes − 1), contando o atacante atual. */
    public static int groupPenalty(int attackers) {
        return -Math.max(0, attackers - 1);
    }

    /** Timing do defensor: +max(0, usos − 1), usos incluindo o ataque atual. */
    public static int timingBonus(int uses) {
        return Math.max(0, uses - 1);
    }

    /** Vulnerável não reage, mas o ataque ainda precisa de total ≥ 10 para acertar. */
    public static final int VULNERABLE_ND = 10;

    public static boolean missesVulnerable(int attackTotal) {
        return attackTotal < VULNERABLE_ND;
    }

    /** Desfecho de um alvo: mitigação em %, se o atacante sofre contra-dano. */
    public record Result(AttackOutcome outcome, int mitigationPct, boolean counterHits) {}

    public static Result resolve(ReactionType reaction, int attackTotal, boolean attackCrit, boolean attackFumble,
                                 int reactionTotal, boolean reactionCrit, boolean breaksGuard) {
        if (attackFumble) return new Result(AttackOutcome.MISS, 0, false);
        if (reaction == ReactionType.NONE) return new Result(AttackOutcome.HIT, 0, false);
        boolean critClash = attackCrit && reactionCrit; // RN-ATQ-05: a reação falha, exceto DEFEND
        return switch (reaction) {
            case DODGE -> !critClash && reactionTotal > attackTotal
                    ? new Result(AttackOutcome.DODGED, 100, false) : new Result(AttackOutcome.HIT, 0, false);
            case DEFEND -> {
                if (critClash) yield new Result(AttackOutcome.DEFENDED, 50, false);
                if (reactionTotal >= attackTotal) {
                    int d = reactionTotal - attackTotal;
                    yield new Result(AttackOutcome.DEFENDED, d >= 8 ? 100 : d >= 4 ? 75 : 50, false);
                }
                yield new Result(AttackOutcome.HIT, breaksGuard ? 0 : 25, false);
            }
            case COUNTER -> {
                if (critClash || reactionTotal <= attackTotal) yield new Result(AttackOutcome.HIT, 0, false);
                // ≥ 1,5 × ataque: o defensor não recebe o dano
                yield new Result(AttackOutcome.COUNTERED, 2 * reactionTotal >= 3 * attackTotal ? 100 : 0, true);
            }
            default -> new Result(AttackOutcome.HIT, 0, false);
        };
    }

    /** Dano aplicado: × (1 − mitigação), arredondado para baixo; mínimo 1 se conectou e mitigação < 100%. */
    public static int damage(int total, int mitigationPct) {
        return damage(total, mitigationPct, 0);
    }

    /** RN-ATQ-03: % da reação, depois a Redução de Dano (Calejamento), depois o mínimo 1. */
    public static int damage(int total, int mitigationPct, int reduction) {
        if (mitigationPct >= 100) return 0;
        return Math.max(1, total * (100 - mitigationPct) / 100 - Math.max(0, reduction));
    }
}
