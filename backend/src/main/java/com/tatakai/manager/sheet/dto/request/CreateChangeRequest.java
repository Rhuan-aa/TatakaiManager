package com.tatakai.manager.sheet.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateChangeRequest(@NotEmpty @Size(max = 40) List<@Valid GmAdjustmentRequest> changes) {}
