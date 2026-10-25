package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.ReactionType;
import jakarta.validation.constraints.NotNull;

public record ReactionRequest(@NotNull ReactionType reaction) {
}
