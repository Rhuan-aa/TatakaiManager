package com.tatakai.manager.npc.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateNpcRequest(

        @NotBlank(message = "nome é obrigatório")
        @Size(max = 100)
        @Pattern(regexp = "^[^<>]*$", message = "nome contém caracteres inválidos")
        String name,

        @Size(max = 2000)
        @Pattern(regexp = "^[^<>]*$", message = "descrição contém caracteres inválidos")
        String description,

        @Valid
        List<NpcDetailDto> knowledge,

        @Valid
        List<NpcDetailDto> specs,

        @NotEmpty(message = "informe ao menos um tipo de interação")
        @Valid
        List<NpcInteractionDto> interactions

) {}
