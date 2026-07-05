package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateLogRequest(

        // Texto livre: não rejeita <>, é sanitizado no backend (NFR-02)
        @NotBlank(message = "a narrativa é obrigatória")
        @Size(max = 5000, message = "a narrativa não pode exceder 5000 caracteres")
        String narrative

) {}
