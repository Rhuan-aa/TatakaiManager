package com.tatakai.manager.martial.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Ficha que vai receber a técnica/base avulsa ensinada. */
public record TeachRequest(@NotNull UUID targetSheetId) {}
