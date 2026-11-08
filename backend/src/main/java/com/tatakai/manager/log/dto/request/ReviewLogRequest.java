package com.tatakai.manager.log.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewLogRequest(

        boolean approved,

        @Size(max = 500, message = "a nota não pode exceder 500 caracteres")
        @Pattern(regexp = "^[^<>]*$", message = "a nota contém caracteres inválidos")
        String note

) {}
