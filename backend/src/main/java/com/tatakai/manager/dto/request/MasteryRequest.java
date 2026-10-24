package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.Mastery;
import jakarta.validation.constraints.NotNull;

public record MasteryRequest(@NotNull Mastery mastery) {}
