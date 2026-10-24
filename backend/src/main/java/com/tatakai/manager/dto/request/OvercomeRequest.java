package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

/** alvo (STR, DEX, CON, INT, CHA, MEN, HP, SP) → PS. */
public record OvercomeRequest(@NotEmpty Map<String, Integer> allocations) {}
