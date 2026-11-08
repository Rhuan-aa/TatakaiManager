package com.tatakai.manager.roll.dto.request;

import com.tatakai.manager.roll.entity.RollChannel;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CampaignRollRequest(
        @NotBlank @Pattern(regexp = "^\\d{1,2}d\\d{1,3}([+-]\\d{1,2}d\\d{1,3}|[+-]\\d{1,3}){0,9}$") String expression,
        @NotBlank @Size(max = 200) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String purpose,
        RollChannel channel,
        UUID customChannelId,
        @Min(-1) @Max(1) Integer advantage,
        Boolean separate) {
    public CampaignRollRequest(String expression, String purpose, RollChannel channel, UUID customChannelId) {
        this(expression, purpose, channel, customChannelId, null, null);
    }
}
