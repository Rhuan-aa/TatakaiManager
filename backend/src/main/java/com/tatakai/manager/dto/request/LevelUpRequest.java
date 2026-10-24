package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.MasteryBonus;
import jakarta.validation.constraints.NotNull;

public record LevelUpRequest(@NotNull MasteryBonus bonus) {}
