package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.TrainingVenue;
import jakarta.validation.constraints.NotNull;

public record SetVenueRequest(@NotNull TrainingVenue venue) {}
