package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record RollChannelRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String name,
        @Size(max = 100) List<UUID> memberIds) {}
