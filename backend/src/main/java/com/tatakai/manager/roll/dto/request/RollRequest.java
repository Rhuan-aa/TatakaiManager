package com.tatakai.manager.roll.dto.request;

import com.tatakai.manager.roll.entity.RollChannel;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** channel (default PUBLIC; GM_PRIVATE só Mestre) e campaignId (necessário em ficha de NPC para publicar). */
public record RollRequest(
        @NotBlank @Pattern(regexp = "^\\d{1,2}d\\d{1,3}([+-]\\d{1,2}d\\d{1,3}|[+-]\\d{1,3}){0,9}$") String expression,
        @NotBlank @Size(max = 200) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String purpose,
        RollChannel channel,
        UUID campaignId,
        UUID customChannelId,
        /** +1 vantagem, -1 desvantagem (rola em dobro); null/0 normal */
        @Min(-1) @Max(1) Integer advantage,
        /** true: cada dado vira uma microrolagem no mesmo cartão; padrão soma tudo */
        Boolean separate
) {
    public RollRequest(String expression, String purpose, RollChannel channel, UUID campaignId, UUID customChannelId) {
        this(expression, purpose, channel, campaignId, customChannelId, null, null);
    }

    public RollRequest(String expression, String purpose) {
        this(expression, purpose, null, null, null, null, null);
    }
}
