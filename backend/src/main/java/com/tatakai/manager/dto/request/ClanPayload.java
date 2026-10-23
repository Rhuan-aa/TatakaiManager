package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.BuildGains;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ClanPayload(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String name,
        @Min(1) @Max(20) Integer rollNumber,
        @Size(max = 4000) @Pattern(regexp = "^[^<>]*$", message = "caracteres < e > não são permitidos") String description,
        BuildGains buildGains,
        List<String> heritage
) {}
