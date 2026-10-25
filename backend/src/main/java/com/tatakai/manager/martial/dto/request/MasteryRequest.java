package com.tatakai.manager.martial.dto.request;

import com.tatakai.manager.martial.entity.Mastery;

import jakarta.validation.constraints.NotNull;

public record MasteryRequest(@NotNull Mastery mastery) {}
