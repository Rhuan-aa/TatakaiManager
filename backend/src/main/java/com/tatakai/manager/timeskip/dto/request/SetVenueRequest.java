package com.tatakai.manager.timeskip.dto.request;

import com.tatakai.manager.progression.entity.TrainingVenue;

import jakarta.validation.constraints.NotNull;

public record SetVenueRequest(@NotNull TrainingVenue venue) {}
