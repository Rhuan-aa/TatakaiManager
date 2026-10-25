package com.tatakai.manager.timeskip.dto.request;

import jakarta.validation.constraints.NotNull;

public record SetBookableRequest(@NotNull Boolean bookable) {}
