package com.tatakai.manager.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SpecDto(

        @NotBlank(message = "nome da spec é obrigatório")
        @Size(max = 100)
        @Pattern(regexp = "^[^<>]*$", message = "spec contém caracteres inválidos")
        String name,

        @Size(max = 2000)
        @Pattern(regexp = "^[^<>]*$", message = "descrição contém caracteres inválidos")
        String description

) {}
