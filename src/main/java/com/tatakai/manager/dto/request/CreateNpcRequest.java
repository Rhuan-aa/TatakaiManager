package com.tatakai.manager.dto.request;

import com.tatakai.manager.entity.InteractionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public record CreateNpcRequest(

        @NotBlank(message = "nome é obrigatório")
        @Size(max = 100)
        @Pattern(regexp = "^[^<>]*$", message = "nome contém caracteres inválidos")
        String name,

        @Size(max = 2000)
        @Pattern(regexp = "^[^<>]*$", message = "descrição contém caracteres inválidos")
        String description,

        @Valid
        NpcAttributesDto attributes,

        @Valid
        List<SpecDto> specs,

        List<@Pattern(regexp = "^[^<>]*$", message = "traço contém caracteres inválidos") String> traits,

        @NotEmpty(message = "informe ao menos um tipo de interação")
        Set<InteractionType> interactionTypes

) {}
