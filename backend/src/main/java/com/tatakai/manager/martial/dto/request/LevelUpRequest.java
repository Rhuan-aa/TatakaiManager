package com.tatakai.manager.martial.dto.request;

import com.tatakai.manager.martial.entity.MasteryBonus;

import jakarta.validation.constraints.NotNull;

public record LevelUpRequest(@NotNull MasteryBonus bonus) {}
