package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.Graduation;
import jakarta.validation.constraints.NotNull;

public record GraduationRequest(@NotNull Graduation graduation) {}
