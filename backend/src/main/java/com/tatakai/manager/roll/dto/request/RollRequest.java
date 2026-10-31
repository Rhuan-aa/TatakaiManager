package com.tatakai.manager.roll.dto.request;

import com.tatakai.manager.roll.entity.RollChannel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** channel (default PUBLIC; GM_PRIVATE só Mestre) e campaignId (necessário em ficha de NPC para publicar). */
public record RollRequest(
        @NotBlank @Pattern(regexp = "^\\d{1,2}d(2|3|4|6|8|10|12|20|100)([+-]\\d{1,3})?$") String expression,
        @NotBlank @Size(max = 200) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String purpose,
        RollChannel channel,
        UUID campaignId,
        UUID customChannelId
) {
    public RollRequest(String expression, String purpose) {
        this(expression, purpose, null, null, null);
    }
}
