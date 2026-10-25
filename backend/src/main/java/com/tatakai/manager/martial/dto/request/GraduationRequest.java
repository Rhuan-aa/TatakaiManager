package com.tatakai.manager.martial.dto.request;

import com.tatakai.manager.catalog.entity.Graduation;

import jakarta.validation.constraints.NotNull;

public record GraduationRequest(@NotNull Graduation graduation) {}
