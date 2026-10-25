package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.ReactionType;
import jakarta.validation.constraints.NotNull;

/** `defenderId`: aliado que reage no lugar de um alvo Inconsciente (Defender ou Contra-atacar). */
public record ReactionRequest(@NotNull ReactionType reaction, java.util.UUID defenderId,
                              /** técnica de núcleo Reação (§38); a reação passa a ser a do tipo da técnica */
                              java.util.UUID techniqueId, String variation) {
}
