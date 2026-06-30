package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "nome é obrigatório")
        @Size(max = 100)
        // Bloqueia tags HTML/scripts no nome (defesa em profundidade contra XSS)
        @Pattern(regexp = "^[^<>]*$", message = "nome contém caracteres inválidos")
        String name,

        @NotBlank(message = "e-mail é obrigatório")
        @Email(message = "e-mail inválido")
        @Size(max = 150)
        String email,

        @NotBlank(message = "senha é obrigatória")
        @Size(min = 8, max = 100, message = "senha deve ter entre 8 e 100 caracteres")
        String password

) {}
