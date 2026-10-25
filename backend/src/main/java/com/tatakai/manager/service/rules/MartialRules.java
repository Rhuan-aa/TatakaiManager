package com.tatakai.manager.service.rules;

import com.tatakai.manager.entity.BasicAttack;
import com.tatakai.manager.entity.CharacterArt;
import com.tatakai.manager.entity.Dominance;
import com.tatakai.manager.entity.Foot;
import com.tatakai.manager.entity.Guard;
import com.tatakai.manager.entity.MartialType;
import com.tatakai.manager.entity.ReactionType;
import com.tatakai.manager.entity.StrikeSide;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Regras puras de interação marcial, especialização, guarda e Calejamento (F6d). Sem Spring, sem aleatoriedade. */
public final class MartialRules {

    private MartialRules() {
    }

    /** Famílias de golpe (spec §6.1); iguais aos ataques básicos, o que o catálogo tem de especialização. */
    public enum Family { SOCO, CHUTE, COTOVELADA, JOELHADA, CABECADA, AGARRAO }

    private static String fold(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    /** "Trocação"→STRIKING, "Derrubada"→TAKEDOWN, "Raspagem"→SWEEP, qualquer texto com "Geral"→GENERAL. */
    public static MartialType type(String raw) {
        String f = fold(raw);
        if (f.contains("geral")) return MartialType.GENERAL;
        if (f.contains("trocacao")) return MartialType.STRIKING;
        if (f.contains("derrubada")) return MartialType.TAKEDOWN;
        if (f.contains("raspagem")) return MartialType.SWEEP;
        return null;
    }

    private static boolean beats(MartialType a, MartialType b) {
        return (a == MartialType.STRIKING && b == MartialType.TAKEDOWN)
                || (a == MartialType.TAKEDOWN && b == MartialType.SWEEP)
                || (a == MartialType.SWEEP && b == MartialType.STRIKING);
    }

    /** +1 atacante vence, −1 defensor vence, 0 nada. GENERAL vence os três e não perde; GENERAL × GENERAL = nada. */
    public static int winner(MartialType atk, MartialType def) {
        if (atk == null || def == null) return 0;
        if (atk == MartialType.GENERAL && def == MartialType.GENERAL) return 0;
        if (atk == MartialType.GENERAL) return 1;
        if (def == MartialType.GENERAL) return -1;
        return beats(atk, def) ? 1 : beats(def, atk) ? -1 : 0;
    }

    /** Resultado final: o Coringa do perdedor anula a vantagem do oponente. */
    public static int interaction(MartialType atk, MartialType def, boolean atkJoker, boolean defJoker) {
        int w = winner(atk, def);
        if (w == 1 && defJoker) return 0;
        if (w == -1 && atkJoker) return 0;
        return w;
    }

    // ---------- especialização ----------

    /** União normalizada das especializações de ataque; "geral" especializa todas as famílias. */
    public static Set<Family> attackSpecs(Collection<String> raw) {
        Set<Family> out = EnumSet.noneOf(Family.class);
        if (raw == null) return out;
        for (String r : raw) {
            String f = fold(r);
            if (f.contains("geral")) return EnumSet.allOf(Family.class);
            if (f.contains("soco")) out.add(Family.SOCO);
            if (f.contains("chute")) out.add(Family.CHUTE);
            if (f.contains("cotovelada")) out.add(Family.COTOVELADA);
            if (f.contains("joelhada")) out.add(Family.JOELHADA);
            if (f.contains("cabecada")) out.add(Family.CABECADA);
            if (f.contains("agarr") || f.contains("clinch") || f.contains("constric") || f.contains("projec")
                    || f.contains("arremess")) out.add(Family.AGARRAO);
        }
        return out;
    }

    /** Reações especializadas (DEFEND, DODGE, COUNTER); "geral" = todas. */
    public static Set<ReactionType> reactionSpecs(Collection<String> raw) {
        Set<ReactionType> out = EnumSet.noneOf(ReactionType.class);
        if (raw == null) return out;
        for (String r : raw) {
            String f = fold(r);
            if (f.contains("geral")) return EnumSet.of(ReactionType.DEFEND, ReactionType.DODGE, ReactionType.COUNTER);
            if (f.contains("defes") || f.contains("defend")) out.add(ReactionType.DEFEND);
            if (f.contains("desvio") || f.contains("esquiv")) out.add(ReactionType.DODGE);
            if (f.contains("contra")) out.add(ReactionType.COUNTER);
        }
        return out;
    }

    public static Family family(BasicAttack b) {
        return Family.valueOf(b.name());
    }

    // ---------- guarda ----------

    public static Guard effectiveGuard(Guard g, boolean knockedDown) {
        return knockedDown ? Guard.FRONTAL : g == null ? Guard.NATURAL : g;
    }

    /** A guarda só vale para golpes de lado único: soco, chute, cotovelada e joelhada. */
    public static boolean oneSided(BasicAttack b) {
        return b == BasicAttack.SOCO || b == BasicAttack.CHUTE || b == BasicAttack.COTOVELADA
                || b == BasicAttack.JOELHADA;
    }

    /** Acerto: frente +1 (Natural/Invertida); trás na Invertida −1, sem o −1 para ambidestro. */
    public static int guardHit(Guard g, StrikeSide side, boolean ambidextrous) {
        if (g == null || g == Guard.FRONTAL || side == null) return 0;
        if (side == StrikeSide.FRONT) return 1;
        return g == Guard.INVERTED && !ambidextrous ? -1 : 0;
    }

    /** Golpe de trás (Natural/Invertida) soma +1d2 de dano. */
    public static boolean guardBackDamage(Guard g, StrikeSide side) {
        return side == StrikeSide.BACK && g != null && g != Guard.FRONTAL;
    }

    /** RN-GUA-01; ambidestro (ou sem dominância) conta como destro. */
    public static Foot leadFoot(Dominance d, Guard g) {
        boolean right = d != Dominance.LEFT;
        boolean inverted = g == Guard.INVERTED;
        return right ^ inverted ? Foot.LEFT : Foot.RIGHT;
    }

    /** Vantagem do Incomum (+1 na reação): defensor com pé direito à frente contra pé esquerdo. */
    public static boolean incomum(Foot defender, Foot attacker, Guard defenderGuard, Guard attackerGuard,
                                  boolean attackerAmbidextrous, boolean attackerReadsDefender) {
        return defender == Foot.RIGHT && attacker == Foot.LEFT
                && defenderGuard != Guard.FRONTAL && attackerGuard != Guard.FRONTAL
                && !attackerAmbidextrous && !attackerReadsDefender;
    }

    // ---------- diversos ----------

    /** Redução de Dano do Calejamento: 2 por ponto; base em Esboço vale metade (floor). */
    public static int calejo(int qty, boolean sketch) {
        return RollRules.sketchHalf(2 * Math.max(0, qty), sketch);
    }

    /** Arte principal padrão: maior graduação; empate pela mais antiga. */
    public static CharacterArt defaultMainArt(List<CharacterArt> active) {
        return active.stream().min(Comparator
                .comparing((CharacterArt a) -> a.getGraduation() == null ? -1 : a.getGraduation().ordinal()).reversed()
                .thenComparing(a -> a.getCreatedAt() == null ? Instant.MAX : a.getCreatedAt())).orElse(null);
    }

    /** Valor do contador depois de um delta, preso a [min, max]. */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
